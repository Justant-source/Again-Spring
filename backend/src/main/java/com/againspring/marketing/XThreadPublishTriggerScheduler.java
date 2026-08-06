package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Unattended 24h marketing auto-publish distributor.
 *
 * <p>After a post is created (human or PLAN), once 24 hours have elapsed:
 * <ul>
 *   <li>{@code x_thread} — every eligible post (alone job, autoPublish)</li>
 *   <li>{@code instagram_reels} + {@code youtube_shorts} — top popular posts under a
 *       KST daily cap of 3 (one dual-target job, same video, autoPublish)</li>
 *   <li>{@code instagram_feed} — remaining posts not selected for video (alone job)</li>
 * </ul>
 *
 * <p>Reels/Shorts and feed are mutually exclusive per post. Popularity ranking is
 * applied in {@link MarketingJobRepository#findPostsEligibleForVideoMarketing}.
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

    private static final int BATCH_LIMIT = 10;
    /** Pull a wider ranked pool so the daily top-3 can be chosen even when X batch is small. */
    private static final int VIDEO_CANDIDATE_LIMIT = 50;
    private static final int DAILY_VIDEO_CAP = 3;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String X_THREAD = "x_thread";
    private static final String INSTAGRAM_FEED = "instagram_feed";
    private static final String INSTAGRAM_REELS = "instagram_reels";
    private static final String YOUTUBE_SHORTS = "youtube_shorts";
    private static final List<String> VIDEO_TARGETS = List.of(INSTAGRAM_REELS, YOUTUBE_SHORTS);

    private final MarketingJobRepository marketingJobRepository;
    private final MarketingJobService marketingJobService;
    private final AsmProperties asmProperties;

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
     * Poll for posts past the 24h mark and enqueue missing X / video / Instagram feed jobs.
     * Interval default: 10 minutes. Order: X → video (under daily cap) → feed (rest).
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
            enqueueEligible(X_THREAD,
                marketingJobRepository.findPostsEligibleForXThreadPublish(since, BATCH_LIMIT),
                "system:x-thread-trigger");
            enqueueVideoJobs(since);
            enqueueEligible(INSTAGRAM_FEED,
                marketingJobRepository.findPostsEligibleForInstagramFeedPublish(since, BATCH_LIMIT),
                "system:instagram-feed-trigger");
        } catch (Exception e) {
            log.error("Error in marketing auto-publish trigger scheduler", e);
        }
    }

    /**
     * Select top popular posts under the KST daily video cap and enqueue dual-target
     * Reels+Shorts jobs. Feed eligibility runs after this so newly selected video posts
     * are excluded from news-card enqueue in the same tick.
     */
    private void enqueueVideoJobs(Instant since) {
        Instant startOfTodayKst = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        long alreadyToday = marketingJobRepository.countVideoJobsCreatedSince(startOfTodayKst);
        int remaining = DAILY_VIDEO_CAP - (int) alreadyToday;
        if (remaining <= 0) {
            log.debug("Daily video marketing cap reached ({} jobs since {}), skipping video enqueue",
                alreadyToday, startOfTodayKst);
            return;
        }

        List<String> candidates = marketingJobRepository.findPostsEligibleForVideoMarketing(
            since, VIDEO_CANDIDATE_LIMIT);
        if (candidates.isEmpty()) {
            log.debug("No posts eligible for video marketing auto-publish");
            return;
        }

        List<String> selected = candidates.subList(0, Math.min(remaining, candidates.size()));
        log.info("Found {} video candidates; enqueuing {} under daily cap (alreadyToday={})",
            candidates.size(), selected.size(), alreadyToday);

        for (String postId : selected) {
            try {
                createVideoJob(postId);
            } catch (Exception e) {
                log.error("Failed to create video marketing job for post {}: {}", postId, e.getMessage());
            }
        }
    }

    private void createVideoJob(String postId) {
        if (marketingJobRepository.countActivePlatformJobs(postId, INSTAGRAM_REELS) > 0
            || marketingJobRepository.countActivePlatformJobs(postId, YOUTUBE_SHORTS) > 0) {
            log.debug("Skipping post {} - active Reels/Shorts job already exists", postId);
            return;
        }

        log.info("Creating Reels+Shorts marketing job for post {}", postId);
        try {
            MarketingJob job = marketingJobService.createJob(
                postId, VIDEO_TARGETS, true, "system:video-marketing-trigger");
            log.info("Created video marketing job {} for post {}", job.getId(), postId);
        } catch (IllegalStateException e) {
            log.warn("Post {} already has an active video marketing job: {}", postId, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Post not found for video auto-publish: {}", postId);
        }
    }

    private void enqueueEligible(String platform, List<String> eligiblePostIds, String requestedBy) {
        if (eligiblePostIds.isEmpty()) {
            log.debug("No posts eligible for {} auto-publish", platform);
            return;
        }

        log.info("Found {} posts eligible for {} auto-publish", eligiblePostIds.size(), platform);
        for (String postId : eligiblePostIds) {
            try {
                createPlatformJob(postId, platform, requestedBy);
            } catch (Exception e) {
                log.error("Failed to create {} marketing job for post {}: {}", platform, postId, e.getMessage());
            }
        }
    }

    private void createPlatformJob(String postId, String platform, String requestedBy) {
        if (marketingJobRepository.countActivePlatformJobs(postId, platform) > 0) {
            log.debug("Skipping post {} - active {} job already exists", postId, platform);
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
