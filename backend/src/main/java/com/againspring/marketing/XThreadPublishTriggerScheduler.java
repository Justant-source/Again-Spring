package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * Poll for posts eligible for X thread publishing
     * Runs at configured interval (default: 10 minutes)
     */
    @Scheduled(fixedDelayString = "${asm.x-thread-poll-interval-ms:600000}")
    public void pollAndPublishToXThread() {
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
        if (marketingJobRepository.hasActivePlatformJob(postId, "x_thread")) {
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
