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
import java.util.List;
import java.util.Objects;

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

    private static final java.time.format.DateTimeFormatter KST_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                    .withZone(java.time.ZoneId.of("Asia/Seoul"));

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

        if (!delayedJobs.isEmpty()) {
            for (MarketingJob delayedJob : delayedJobs) {
                long delayMinutes = (now.toEpochMilli() - delayedJob.getScheduledPublishAt().toEpochMilli()) / 60_000;
                log.warn("Marketing job {} READY but {} minutes past scheduled time {}",
                    delayedJob.getId(), delayMinutes, KST_FORMAT.format(delayedJob.getScheduledPublishAt()));
                telegramNotifier.send(String.format(
                    "⚠️ [Again-Spring] 마케팅 발행 지연%n" +
                    "잡 #%d · post=%s%n" +
                    "상태: READY · 채널: %s%n" +
                    "예약: %s · 경과: %d분%n" +
                    "조치: ASM/렌더링 지연 감시, 필요시 수동 발행 고려",
                    delayedJob.getId(),
                    delayedJob.getPostId() != null ? delayedJob.getPostId() : "?",
                    delayedJob.getTargets() != null ? delayedJob.getTargets() : "[]",
                    KST_FORMAT.format(delayedJob.getScheduledPublishAt()),
                    delayMinutes));
            }
        }
    }

    @Scheduled(fixedDelayString = "${asm.poll-interval-ms:15000}")
    public void pollJobs() {
        if (!asmProperties.isEnabled()) {
            return;
        }

        List<MarketingJob> jobsToPoll = marketingJobRepository.findByStatusIn(POLLING_STATUSES);
        Instant now = Instant.now();

        // Evening-slot auto-publish: READY + autoPublish + scheduledPublishAt <= now
        List<MarketingJob> duePublish = marketingJobRepository.findDueAutoPublishJobs(now);
        if (!duePublish.isEmpty()) {
            log.info("Found {} marketing jobs due for evening-slot publish", duePublish.size());
            for (MarketingJob due : duePublish) {
                try {
                    marketingJobService.triggerPublish(due.getId());
                    log.info("Triggered scheduled publish for marketing job {} (slot={})",
                        due.getId(), due.getScheduledPublishAt());
                } catch (Exception e) {
                    log.warn("Failed to trigger scheduled publish for job {}: {}",
                        due.getId(), e.getMessage());
                    notifyTriggerFailureOnce(due, e);
                }
            }
        }

        // Poll ASM first. Carry-over MUST run after this loop: applyPoll saves the
        // in-memory entity and would otherwise overwrite a just-written next-day slot,
        // re-triggering Telegram "이월" every pollInterval (~15s) with count stuck at 1.
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
                        triggerImmediatelyWhenReadyAfterSlot(job, now);
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

        // Detect and reschedule auto-publish jobs that missed the slot while still generating
        List<MarketingJob> expiredJobs = marketingJobRepository.findExpiredScheduledJobs();
        if (!expiredJobs.isEmpty()) {
            log.info("Found {} expired scheduled jobs, rescheduling...", expiredJobs.size());
            for (MarketingJob expiredJob : expiredJobs) {
                rescheduleExpiredJob(expiredJob, now);
            }
        }
    }

    /**
     * Rendering may finish after the evening slot. Publish the newly READY artifact in
     * this same reconciliation cycle; do not defer it to tomorrow or wait for another poll.
     */
    private void triggerImmediatelyWhenReadyAfterSlot(MarketingJob job, Instant now) {
        if (!"READY".equals(job.getStatus()) || !Boolean.TRUE.equals(job.getAutoPublish())
            || job.getScheduledPublishAt() == null || job.getScheduledPublishAt().isAfter(now)) {
            return;
        }
        try {
            marketingJobService.triggerPublish(job.getId());
            log.info("Immediately triggered late READY marketing job {} (slot={})",
                job.getId(), job.getScheduledPublishAt());
        } catch (Exception e) {
            log.warn("Failed to immediately trigger late READY job {}: {}", job.getId(), e.getMessage());
            notifyTriggerFailureOnce(job, e);
        }
    }

    /**
     * Reschedule an expired scheduled job to the next day at the same time.
     * If collision detected (another job scheduled within ±5 minutes), find next available slot.
     *
     * Slot logic:
     * - 30-minute slot: increments by 30 minutes on collision
     * - On-the-hour slot: increments by 1 hour on collision
     *
     * @param job the expired job to reschedule
     * @param now current time
     */
    private void rescheduleExpiredJob(MarketingJob job, Instant now) {
        Instant originalScheduledAt = job.getScheduledPublishAt();
        if (originalScheduledAt == null) {
            // After NOT NULL migration (Task C), this should never occur.
            // If it does, it's a code defect and must be loud.
            log.error("Code defect: Expired job {} has null scheduledPublishAt — DB constraint should prevent this", job.getId());
            telegramNotifier.send(String.format(
                "🚨 [Again-Spring] 마케팅 코드 결함 - 발행 시각 NULL%n" +
                "잡 #%d · post=%s%n" +
                "원인: scheduledPublishAt이 NOT NULL이어야 하는데 NULL 발견%n" +
                "조치: DB 제약이 실패했거나 마이그레이션이 불완전함. 즉시 조사 필요",
                job.getId(),
                job.getPostId() != null ? job.getPostId() : "?"));
            return;
        }

        // Calculate next day at same time
        Instant nextDayTime = originalScheduledAt.plus(1, ChronoUnit.DAYS);
        Instant newScheduledTime = findAvailableSlot(nextDayTime, job.getId());

        // Set original scheduled time on first reschedule
        if (job.getOriginalScheduledAt() == null) {
            job.setOriginalScheduledAt(originalScheduledAt);
        }

        int nextCount = (job.getRescheduledCount() == null ? 0 : job.getRescheduledCount()) + 1;

        // Update job with new schedule
        job.setScheduledPublishAt(newScheduledTime);
        job.setRescheduledCount(nextCount);
        job.setLastRescheduledAt(now);
        job.setRescheduledReason("예약 시각 경과 (원 예약: " + originalScheduledAt + ")");

        marketingJobRepository.save(job);

        log.info("Rescheduled expired marketing job {} from {} to {} (reschedule #{}, reason: {})",
            job.getId(), originalScheduledAt, newScheduledTime, nextCount,
            job.getRescheduledReason());

        telegramNotifier.send(String.format(
            "⚠️ [Again-Spring] 마케팅 예약 발행 이월%n잡 #%d — %s → %s (원 예약 %s, %d회째 이월)",
            job.getId(),
            KST_FORMAT.format(originalScheduledAt),
            KST_FORMAT.format(newScheduledTime),
            KST_FORMAT.format(job.getOriginalScheduledAt()),
            nextCount));
    }

    /**
     * Find available time slot, incrementing from the target time if collision detected.
     *
     * @param targetTime the target scheduled time
     * @param excludeJobId the job ID to exclude from collision check (self)
     * @return the first available slot (no collision)
     */
    private Instant findAvailableSlot(Instant targetTime, Long excludeJobId) {
        List<MarketingJob> conflicting = marketingJobRepository.findJobsByScheduledTimeRange(
            targetTime, excludeJobId);

        // No collision, return target time
        if (conflicting.isEmpty()) {
            return targetTime;
        }

        // Detect slot increment (30-min or 1-hour)
        int minute = extractMinute(targetTime);
        long incrementMs;

        if (minute == 0) {
            // On-the-hour slot: increment by 1 hour
            incrementMs = 60 * 60 * 1000L;
        } else if (minute == 30) {
            // 30-minute slot: increment by 30 minutes
            incrementMs = 30 * 60 * 1000L;
        } else {
            // Arbitrary minute: default to 30-minute increment
            incrementMs = 30 * 60 * 1000L;
        }

        // Recursively find next slot
        Instant nextSlot = targetTime.plusMillis(incrementMs);
        return findAvailableSlot(nextSlot, excludeJobId);
    }

    /**
     * Extract minute component from an Instant (UTC-based).
     * Used to determine slot type (on-the-hour vs 30-min).
     *
     * @param time the instant
     * @return minute (0-59)
     */
    private int extractMinute(Instant time) {
        long epochMilli = time.toEpochMilli();
        long minutesInDay = (epochMilli / (60 * 1000L)) % (24 * 60);
        return (int) (minutesInDay % 60);
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
