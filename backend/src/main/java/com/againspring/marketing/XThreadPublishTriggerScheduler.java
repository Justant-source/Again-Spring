package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Scheduler for triggering X thread marketing job creation
 * Automatically publishes posts to X (Twitter) as threads when conditions are met:
 * - 24 hours have passed since post creation
 * - Post has >= 6 comments
 * - No active x_thread job exists for the post
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XThreadPublishTriggerScheduler {

    private final AsmClient asmClient;
    private final MarketingJobRepository marketingJobRepository;
    private final MarketingJobService marketingJobService;
    private final AsmProperties asmProperties;

    /**
     * Opt-in gate, separate from {@code asmProperties.isEnabled()}.
     *
     * ASM is a single instance shared by dev and prod (one WSL box, one X account —
     * see docs/shared/marketing/README.md). Every other marketing platform is triggered
     * by an explicit admin click, so a stray dev deployment is harmless. This scheduler
     * is a fully unattended cron with no human in the loop, so if it's on, it publishes
     * to the real @againspring_net account on whatever interval it's given — dev and
     * prod alike. Defaults to false so a plain dev redeploy can never auto-publish;
     * set true only in .env.prod once the feature is meant to go live there.
     *
     * (Found the hard way on 2026-07-31: a dev redeploy immediately created 10 jobs
     * with auto_publish=true against the shared ASM instance; 2 reached the live
     * account — 4 real tweets — before the ASM worker could be stopped. Cleaned up
     * by hand; this flag exists so it can't happen from a dev deploy again.)
     */
    @Value("${asm.x-thread-publish-trigger-enabled:false}")
    private boolean triggerEnabled;

    /**
     * Poll for posts eligible for X thread publishing
     * Runs at configured interval (default: 10 minutes)
     */
    @Scheduled(fixedDelayString = "${asm.x-thread-poll-interval-ms:600000}")
    public void pollAndPublishToXThread() {
        if (!triggerEnabled) {
            log.debug("X thread publish trigger is disabled (asm.x-thread-publish-trigger-enabled=false), skipping");
            return;
        }
        if (!asmProperties.isEnabled()) {
            log.debug("ASM is disabled, skipping X thread publish trigger");
            return;
        }

        try {
            // Find up to 10 posts eligible for X thread publishing
            List<String> eligiblePostIds = marketingJobRepository.findPostsEligibleForXThreadPublish(10);
            if (eligiblePostIds.isEmpty()) {
                log.debug("No posts eligible for X thread publishing");
                return;
            }

            log.info("Found {} posts eligible for X thread publishing", eligiblePostIds.size());

            for (String postId : eligiblePostIds) {
                try {
                    publishPostToXThread(postId);
                } catch (Exception e) {
                    log.error("Failed to create X thread marketing job for post {}: {}", postId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in X thread publish trigger scheduler", e);
        }
    }

    /**
     * Create a marketing job for X thread publishing
     */
    private void publishPostToXThread(String postId) {
        // Double-check that no active x_thread job exists for this post
        if (marketingJobRepository.countActivePlatformJobs(postId, "x_thread") > 0) {
            log.debug("Skipping post {} - x_thread job already exists", postId);
            return;
        }

        List<String> targets = Arrays.asList("x_thread");
        String requestedBy = "system:x-thread-trigger";
        boolean autoPublish = true;

        log.info("Creating X thread marketing job for post {}", postId);
        try {
            MarketingJob job = marketingJobService.createJob(postId, targets, autoPublish, requestedBy);
            log.info("Created X thread marketing job {} for post {}", job.getId(), postId);
        } catch (IllegalStateException e) {
            // Already processing error
            log.warn("Post {} already has an active marketing job: {}", postId, e.getMessage());
        } catch (IllegalArgumentException e) {
            // Post not found
            log.warn("Post not found: {}", postId);
        }
    }
}
