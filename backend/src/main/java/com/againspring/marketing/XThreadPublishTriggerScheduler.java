package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Unattended 24h marketing auto-publish distributor.
 *
 * <p>After a post is created (human or PLAN), once 24 hours have elapsed and the
 * shared KST daily quota still has room:
 * <ul>
 *   <li>{@code instagram_reels} + {@code youtube_shorts} — popular posts first,
 *       under {@code dailyVideoCap} and the shared pool (dual-target job, no X)</li>
 *   <li>{@code x_thread} + {@code instagram_feed} — remaining pool slots
 *       ({@code dailyTextCap - videosToday}), each as alone jobs</li>
 * </ul>
 *
 * <p>Caps are stored in {@code system_setting} via {@link MarketingQuotaService}
 * (defaults: text/pool 6, video 3). Manual admin jobs count toward the same day totals.
 *
 * <p>Only posts with {@code createdAt >= asm.auto-publish-since} are eligible.
 * The cutoff is fail-closed: if unset while the trigger is on, the scheduler
 * skips (2026-08-02 backlog flood).
 *
 * <p>Opt-in via {@code asm.x-thread-publish-trigger-enabled} (shared gate for all
 * channels — historical name kept so existing .env keeps working). Defaults
 * false so a plain dev redeploy cannot publish to the live shared ASM account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XThreadPublishTriggerScheduler {

    /** Pull a wider ranked pool so daily top-N can be chosen even when the batch is small. */
    private static final int CANDIDATE_LIMIT = 50;

    private static final String X_THREAD = "x_thread";
    private static final String INSTAGRAM_FEED = "instagram_feed";
    private static final String INSTAGRAM_REELS = "instagram_reels";
    private static final String YOUTUBE_SHORTS = "youtube_shorts";
    private static final List<String> VIDEO_TARGETS = List.of(INSTAGRAM_REELS, YOUTUBE_SHORTS);

    private final MarketingJobRepository marketingJobRepository;
    private final MarketingJobService marketingJobService;
    private final AsmProperties asmProperties;
    private final MarketingQuotaService marketingQuotaService;

    /**
     * Opt-in gate, separate from {@code asmProperties.isEnabled()}.
     *
     * ASM is a single instance shared by dev and prod (one WSL box, one X/IG account —
     * see docs/shared/marketing/README.md). This scheduler is fully unattended; if it's
     * on, it publishes to the real accounts. Defaults to false so a plain dev redeploy
     * can never auto-publish. Set true only where auto-publish is intentional.
     *
     * (Found the hard way on 2026-07-31: a dev redeploy immediately created 10 jobs
     * with auto_publish=true against the shared ASM instance; 2 reached the live
     * account — 4 real tweets — before the ASM worker could be stopped.)
     */
    @Value("${asm.x-thread-publish-trigger-enabled:false}")
    private boolean triggerEnabled;

    /**
     * Poll for posts past the 24h mark and enqueue missing video / text jobs under
     * the shared daily quota. Interval default: 10 minutes. Order: video → text.
     */
    @Scheduled(fixedDelayString = "${asm.x-thread-poll-interval-ms:600000}")
    public void pollAndPublishToXThread() {
        if (!triggerEnabled) {
            log.debug("Marketing auto-publish trigger is disabled (asm.x-thread-publish-trigger-enabled=false), skipping");
            return;
        }
        if (!asmProperties.isEnabled()) {
            log.debug("ASM is disabled, skipping marketing auto-publish trigger");
            return;
        }

        Instant since = parseAutoPublishSince(asmProperties.getAutoPublishSince());
        if (since == null) {
            log.warn("Marketing auto-publish trigger is on but asm.auto-publish-since is unset/invalid — fail-closed, skipping");
            return;
        }

        try {
            MarketingQuotaService.Caps caps = marketingQuotaService.getCaps();
            Instant startOfTodayKst = marketingQuotaService.startOfTodayKst();
            long videosToday = marketingJobRepository.countVideoJobsCreatedSince(startOfTodayKst);
            long textsToday = marketingJobRepository.countTextSlotsCreatedSince(startOfTodayKst);
            long remainingPool = caps.dailyTextCap() - videosToday - textsToday;
            if (remainingPool <= 0) {
                log.debug("Daily marketing pool exhausted (textCap={}, videosToday={}, textsToday={})",
                    caps.dailyTextCap(), videosToday, textsToday);
                return;
            }

            int enqueuedVideos = enqueueVideoJobs(since, caps, videosToday, remainingPool);
            long textSlots = caps.dailyTextCap() - (videosToday + enqueuedVideos) - textsToday;
            if (textSlots > 0) {
                enqueueTextJobs(since, (int) textSlots);
            }
        } catch (Exception e) {
            log.error("Error in marketing auto-publish trigger scheduler", e);
        }
    }

    /**
     * Select top popular posts under videoCap and remaining shared pool; enqueue
     * dual-target Reels+Shorts jobs. Returns how many jobs were successfully created.
     */
    private int enqueueVideoJobs(Instant since, MarketingQuotaService.Caps caps,
                                 long videosToday, long remainingPool) {
        int videoSlots = (int) Math.min(
            Math.min(caps.dailyVideoCap() - videosToday, remainingPool),
            Integer.MAX_VALUE
        );
        if (videoSlots <= 0) {
            log.debug("Daily video marketing slots exhausted (videoCap={}, videosToday={}, remainingPool={})",
                caps.dailyVideoCap(), videosToday, remainingPool);
            return 0;
        }

        List<String> candidates = marketingJobRepository.findPostsEligibleForVideoMarketing(
            since, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            log.debug("No posts eligible for video marketing auto-publish");
            return 0;
        }

        List<String> selected = candidates.subList(0, Math.min(videoSlots, candidates.size()));
        log.info("Found {} video candidates; enqueuing up to {} (videosToday={}, remainingPool={})",
            candidates.size(), selected.size(), videosToday, remainingPool);

        int created = 0;
        for (String postId : selected) {
            try {
                if (createVideoJob(postId)) {
                    created++;
                }
            } catch (Exception e) {
                log.error("Failed to create video marketing job for post {}: {}", postId, e.getMessage());
            }
        }
        return created;
    }

    private void enqueueTextJobs(Instant since, int textSlots) {
        List<String> candidates = marketingJobRepository.findPostsEligibleForTextMarketing(
            since, CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            log.debug("No posts eligible for text marketing auto-publish");
            return;
        }

        List<String> selected = candidates.subList(0, Math.min(textSlots, candidates.size()));
        log.info("Found {} text candidates; enqueuing {} under remaining pool",
            candidates.size(), selected.size());

        for (String postId : selected) {
            try {
                createPlatformJob(postId, X_THREAD, "system:x-thread-trigger");
                createPlatformJob(postId, INSTAGRAM_FEED, "system:instagram-feed-trigger");
            } catch (Exception e) {
                log.error("Failed to create text marketing jobs for post {}: {}", postId, e.getMessage());
            }
        }
    }

    /** @return true if a new video job was created */
    private boolean createVideoJob(String postId) {
        if (marketingJobRepository.countActivePlatformJobs(postId, INSTAGRAM_REELS) > 0
            || marketingJobRepository.countActivePlatformJobs(postId, YOUTUBE_SHORTS) > 0) {
            log.debug("Skipping post {} - active Reels/Shorts job already exists", postId);
            return false;
        }

        log.info("Creating Reels+Shorts marketing job for post {}", postId);
        try {
            MarketingJob job = marketingJobService.createJob(
                postId, VIDEO_TARGETS, true, "system:video-marketing-trigger");
            log.info("Created video marketing job {} for post {}", job.getId(), postId);
            return true;
        } catch (IllegalStateException e) {
            log.warn("Post {} already has an active video marketing job: {}", postId, e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("Post not found for video auto-publish: {}", postId);
            return false;
        }
    }

    private void createPlatformJob(String postId, String platform, String requestedBy) {
        if (marketingJobRepository.countActivePlatformJobs(postId, platform) > 0) {
            log.debug("Skipping post {} - active {} job already exists", postId);
            return;
        }

        log.info("Creating {} marketing job for post {}", platform, postId);
        try {
            MarketingJob job = marketingJobService.createJob(
                postId, List.of(platform), true, requestedBy);
            log.info("Created {} marketing job {} for post {}", platform, job.getId(), postId);
        } catch (IllegalStateException e) {
            log.warn("Post {} already has an active {} marketing job: {}", platform, postId, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Post not found for {} auto-publish: {}", platform, postId);
        }
    }

    /** Blank/null/unparseable → null (caller fail-closes). Accepts ISO-8601 Instant. */
    static Instant parseAutoPublishSince(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
