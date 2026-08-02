package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Unattended 24h marketing auto-publish for X thread + Instagram feed.
 *
 * <p>After a post is created (human or PLAN), once 24 hours have elapsed and no
 * prior job exists for that platform, creates one alone ASM job per channel with
 * {@code autoPublish=true}. Comment-count gates are intentionally not applied —
 * product rule (2026-08-02): always publish after 24h.
 *
 * <p>Opt-in via {@code asm.x-thread-publish-trigger-enabled} (shared gate for both
 * channels — historical name kept so existing .env.prod keeps working). Defaults
 * false so a plain dev redeploy cannot publish to the live shared ASM account.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XThreadPublishTriggerScheduler {

    private static final int BATCH_LIMIT = 10;
    private static final String X_THREAD = "x_thread";
    private static final String INSTAGRAM_FEED = "instagram_feed";

    private final MarketingJobRepository marketingJobRepository;
    private final MarketingJobService marketingJobService;
    private final AsmProperties asmProperties;

    /**
     * Opt-in gate, separate from {@code asmProperties.isEnabled()}.
     *
     * ASM is a single instance shared by dev and prod (one WSL box, one X/IG account —
     * see docs/shared/marketing/README.md). This scheduler is fully unattended; if it's
     * on, it publishes to the real accounts. Defaults to false so a plain dev redeploy
     * can never auto-publish. Set true only in .env.prod.
     *
     * (Found the hard way on 2026-07-31: a dev redeploy immediately created 10 jobs
     * with auto_publish=true against the shared ASM instance; 2 reached the live
     * account — 4 real tweets — before the ASM worker could be stopped.)
     */
    @Value("${asm.x-thread-publish-trigger-enabled:false}")
    private boolean triggerEnabled;

    /**
     * Poll for posts past the 24h mark and enqueue missing X / Instagram jobs.
     * Interval default: 10 minutes.
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

        try {
            enqueueEligible(X_THREAD, marketingJobRepository.findPostsEligibleForXThreadPublish(BATCH_LIMIT),
                "system:x-thread-trigger");
            enqueueEligible(INSTAGRAM_FEED, marketingJobRepository.findPostsEligibleForInstagramFeedPublish(BATCH_LIMIT),
                "system:instagram-feed-trigger");
        } catch (Exception e) {
            log.error("Error in marketing auto-publish trigger scheduler", e);
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
            log.warn("Post {} already has an active {} marketing job: {}", postId, platform, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Post not found for {} auto-publish: {}", platform, postId);
        }
    }
}
