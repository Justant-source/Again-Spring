package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polling scheduler for marketing jobs
 * Periodically checks the status of non-terminal jobs with ASM
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingPollingScheduler {

    private final AsmClient asmClient;
    private final MarketingJobRepository marketingJobRepository;
    private final MarketingJobService marketingJobService;
    private final AsmProperties asmProperties;
    private final TelegramNotifier telegramNotifier;

    private static final long RETRY_AUTO_DELAY_MS = 5 * 60 * 1000L; // 5 minutes

    private static final List<String> POLLING_STATUSES = Arrays.asList(
        "QUEUED", "RUNNING", "SLA_BREACHED", "WAITING_EXTERNAL", "READY", "PUBLISHING", "STALE"
    );

    private static final List<String> TERMINAL_STATUSES = Arrays.asList(
        "PUBLISHED", "FAILED", "PARTIAL"
    );

    private static final long MAX_STALE_AGE_MS = 24 * 60 * 60 * 1000L;
    private static final long ASM_CIRCUIT_OPEN_MS = 300_000L;

    /** When set, skip ASM GETs until this instant (shared outage backoff). */
    private volatile Instant asmCircuitOpenUntil = Instant.EPOCH;

    /**
     * Jobs already told about the current READY stall. Cleared when the job leaves the
     * delayed set, so a later stall alerts again. A process restart may alert once more.
     */
    private final Set<Long> delayNotifiedJobIds = ConcurrentHashMap.newKeySet();

    /**
     * Auto-retry transient LLM failures at the job level (Decision #4).
     *
     * <p>VARIANT_LLM_ERROR failures that are marked retryable are automatically regenerated
     * 5 minutes after initial failure, up to a maximum of 2 total attempts (initial + 1 retry).
     * This gives infrastructure time to recover from transient conditions (rate limits, timeouts)
     * without user intervention.
     *
     * <p>Runs every 60 seconds to keep retry latency low.
     */
    @Scheduled(fixedDelayString = "${marketing.retry-auto-interval-ms:60000}")
    public void autoRetryTransientFailures() {
        if (!asmProperties.isEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Instant fiveMinutesAgo = now.minus(5, ChronoUnit.MINUTES);

        // Find FAILED jobs that are retryable, code matches transient patterns, and updated > 5 min ago
        List<MarketingJob> retryableJobs = marketingJobRepository.findAll().stream()
            .filter(job -> "FAILED".equals(job.getStatus()))
            .filter(job -> Boolean.TRUE.equals(job.getRetryable()))
            .filter(job -> job.getFailureCode() != null &&
                (job.getFailureCode().startsWith("VARIANT_") ||
                 job.getFailureCode().startsWith("SIBOM_") ||
                 job.getFailureCode().startsWith("DURATION_") ||
                 job.getFailureCode().startsWith("LAYOUT_")))
            .filter(job -> job.getGenerationAttempt() == null || job.getGenerationAttempt() < 2)
            .filter(job -> job.getUpdatedAt() != null && job.getUpdatedAt().isBefore(fiveMinutesAgo))
            // Exclude jobs that already have a retry child
            .filter(job -> !hasRetryChild(job.getId()))
            .toList();

        if (!retryableJobs.isEmpty()) {
            log.info("Found {} transient failures eligible for auto-retry", retryableJobs.size());
            for (MarketingJob job : retryableJobs) {
                try {
                    MarketingJob retried = marketingJobService.regenerateJob(job.getId(), "system:auto-retry");
                    log.info("Auto-retried job {} -> new job {}", job.getId(), retried.getId());
                } catch (Exception e) {
                    log.warn("Failed to auto-retry job {}: {}", job.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Check if a job has a retry child (indicates retry already attempted).
     */
    private boolean hasRetryChild(Long jobId) {
        return marketingJobRepository.findAll().stream()
            .anyMatch(job -> jobId.equals(job.getRetryOfJobId()));
    }

    /**
     * Monitor READY jobs that are past their scheduled publish time by 30+ minutes.
     * Alerts on operational delays (Decision #10 monitoring).
     * Runs every 5 minutes to catch delays early.
     */
    @Scheduled(fixedDelayString = "${marketing.monitor-delay-interval-ms:300000}")
    public void monitorPublishingDelays() {
        if (!asmProperties.isEnabled()) {
            return;
        }
        Instant now = Instant.now();
        Instant thirtyMinutesAgo = now.minus(30, ChronoUnit.MINUTES);
        List<MarketingJob> delayedJobs = marketingJobRepository.findReadyJobsPastScheduleBy30Minutes(thirtyMinutesAgo);

        Set<Long> stillDelayed = new HashSet<>();
        for (MarketingJob delayedJob : delayedJobs) {
            Long jobId = delayedJob.getId();
            if (jobId != null) {
                stillDelayed.add(jobId);
                if (!delayNotifiedJobIds.add(jobId)) {
                    continue;
                }
            }
            Instant readySince = delayedJob.getUpdatedAt() != null
                ? delayedJob.getUpdatedAt() : delayedJob.getCreatedAt();
            long delayMinutes = readySince == null ? 0
                : (now.toEpochMilli() - readySince.toEpochMilli()) / 60_000;
            log.warn("Marketing job {} READY auto-publish stuck for {} minutes",
                delayedJob.getId(), delayMinutes);
            telegramNotifier.send(String.format(
                "⚠️ [Again-Spring] 마케팅 발행 지연%n" +
                "잡 #%d · post=%s%n" +
                "상태: READY · 채널: %s%n" +
                "READY 이후: %d분%n" +
                "조치: ASM publish 트리거 실패 여부 확인, 필요시 수동 발행",
                delayedJob.getId(),
                delayedJob.getPostId() != null ? delayedJob.getPostId() : "?",
                delayedJob.getTargets() != null ? delayedJob.getTargets() : "[]",
                delayMinutes));
        }
        delayNotifiedJobIds.retainAll(stillDelayed);
    }

    @Scheduled(fixedDelayString = "${asm.poll-interval-ms:15000}")
    public void pollJobs() {
        if (!asmProperties.isEnabled()) {
            return;
        }

        List<MarketingJob> jobsToPoll = marketingJobRepository.findByStatusIn(POLLING_STATUSES);
        Instant now = Instant.now();

        // READY auto-publish: fire as soon as artifacts exist. No evening slot.
        List<MarketingJob> duePublish = marketingJobRepository.findDueAutoPublishJobs(now);
        if (!duePublish.isEmpty()) {
            log.info("Found {} READY marketing jobs due for immediate auto-publish", duePublish.size());
            for (MarketingJob due : duePublish) {
                try {
                    marketingJobService.triggerPublish(due.getId());
                    log.info("Triggered immediate auto-publish for marketing job {}", due.getId());
                } catch (Exception e) {
                    log.warn("Failed to trigger scheduled publish for job {}: {}",
                        due.getId(), e.getMessage());
                    handlePublishTriggerFailure(due, e);
                }
            }
        }

        // Poll ASM. READY jobs with cached artifacts skip GET; publish is findDueAutoPublishJobs.
        if (!jobsToPoll.isEmpty()) {
            if (now.isBefore(asmCircuitOpenUntil)) {
                log.debug("ASM circuit open until {}; skipping {} job polls", asmCircuitOpenUntil, jobsToPoll.size());
            } else {
                log.debug("Polling {} marketing jobs", jobsToPoll.size());
                for (MarketingJob job : jobsToPoll) {
                    // Completed preview: artifacts already cached — do not hammer ASM.
                    // Publish is driven by findDueAutoPublishJobs / triggerPublish, not this poll.
                    if ("READY".equals(job.getStatus()) && job.hasArtifacts()) {
                        continue;
                    }
                    if ("STALE".equals(job.getStatus()) && job.hasArtifacts()) {
                        job.setStatus("READY");
                        job.setPollFailCount(0);
                        job.setErrorMessage(null);
                        marketingJobRepository.save(job);
                        continue;
                    }
                    if ("STALE".equals(job.getStatus())) {
                        // 24h expiry → permanent FAILED
                        if (job.getCreatedAt() != null &&
                            now.toEpochMilli() - job.getCreatedAt().toEpochMilli() > MAX_STALE_AGE_MS) {
                            marketingJobService.failJob(job, MarketingFailureStage.ASM_POLL, "ASM_24H_TIMEOUT", false,
                                "ASM 응답 24시간 초과 — 자동 실패 처리");
                            continue;
                        }
                        // Exponential backoff: skip if not enough time has passed
                        if (job.getLastPolledAt() != null) {
                            long backoffMs = Math.min(
                                (long) job.getPollFailCount() * asmProperties.getPollIntervalMs(),
                                300_000L  // max 5 min
                            );
                            if (now.toEpochMilli() - job.getLastPolledAt().toEpochMilli() < backoffMs) {
                                continue;
                            }
                        }
                    }
                    try {
                        AsmJobView view = asmClient.getJob(job.getRemoteJobId());
                        marketingJobService.applyPoll(job, view);
                        log.debug("Polled job {} -> status: {}", job.getId(), view.getStatus());
                        triggerImmediatelyWhenReady(job);
                    } catch (AsmUnavailableException e) {
                        log.warn("ASM unavailable when polling job {}: {}", job.getId(), e.getMessage());
                        job.markPollFailure(e.getMessage());
                        marketingJobRepository.save(job);
                        asmCircuitOpenUntil = Instant.now().plusMillis(ASM_CIRCUIT_OPEN_MS);
                        log.warn("ASM circuit open for {}ms after connect failure", ASM_CIRCUIT_OPEN_MS);
                        break;
                    }
                }
            }
        }

    }

    /**
     * Rendering finished: publish in this same cycle. Auto-publish does not wait for a clock slot.
     */
    private void triggerImmediatelyWhenReady(MarketingJob job) {
        if (!"READY".equals(job.getStatus()) || !Boolean.TRUE.equals(job.getAutoPublish())) {
            return;
        }
        try {
            marketingJobService.triggerPublish(job.getId());
            log.info("Immediately triggered READY marketing job {}", job.getId());
        } catch (Exception e) {
            log.warn("Failed to immediately trigger READY job {}: {}", job.getId(), e.getMessage());
            handlePublishTriggerFailure(job, e);
        }
    }

    /**
     * ASM already left READY (PARTIAL, PUBLISHED, or FAILED). Sync that status and do not republish.
     * A successful sync is the one terminal alert from {@code applyPoll}; the trigger-failure
     * telegram is reserved for conflicts we could not reconcile.
     *
     * @return true when the local job is now PARTIAL, PUBLISHED, or FAILED
     */
    private boolean reconcileTerminalPublishConflict(MarketingJob job, Exception error) {
        if (!isTerminalPublishConflict(error) || job.getRemoteJobId() == null) {
            return false;
        }
        try {
            AsmJobView view = asmClient.getJob(job.getRemoteJobId());
            marketingJobService.applyPoll(job, view);
            String status = job.getStatus();
            if ("PARTIAL".equals(status) || "PUBLISHED".equals(status) || "FAILED".equals(status)) {
                log.info("Reconciled marketing job {} to ASM status {} after publish conflict",
                    job.getId(), status);
                return true;
            }
            log.warn("Publish conflict for job {} but polled status is still {}", job.getId(), status);
            return false;
        } catch (Exception reconcileError) {
            log.warn("Failed to reconcile publish conflict for job {}: {}",
                job.getId(), reconcileError.getMessage());
            return false;
        }
    }

    static boolean isTerminalPublishConflict(Exception error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || !message.contains("409")) {
            return false;
        }
        return message.contains("current status: PARTIAL")
            || message.contains("current status: PUBLISHED")
            || message.contains("current status: FAILED");
    }

    private void handlePublishTriggerFailure(MarketingJob job, Exception error) {
        if (reconcileTerminalPublishConflict(job, error)) {
            return;
        }
        notifyTriggerFailureOnce(job, error);
    }

    /** Persist the error marker so a due job that retries every poll does not spam Telegram. */
    private void notifyTriggerFailureOnce(MarketingJob job, Exception error) {
        String logDetail = error.getClass().getSimpleName() + ": " +
            (error.getMessage() != null ? error.getMessage().replaceAll("[\\r\\n\\t]+", " ") : "메시지 없음");
        String marker = "예약 발행 트리거 실패: " + logDetail;
        if (Objects.equals(marker, job.getErrorMessage())) return;
        job.setErrorMessage(marker);
        marketingJobRepository.save(job);
        telegramNotifier.send(String.format(
            "❌ [Again-Spring] 예약 마케팅 발행 트리거 실패%n잡 #%s · post=%s%n원인: %s%n에러 로그: %s",
            job.getId(), job.getPostId() != null ? job.getPostId() : "?",
            error.getClass().getSimpleName(), logDetail));
    }
}
