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

    private static final java.time.format.DateTimeFormatter KST_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
                    .withZone(java.time.ZoneId.of("Asia/Seoul"));

    private static final List<String> POLLING_STATUSES = Arrays.asList(
        "QUEUED", "RUNNING", "READY", "PUBLISHING", "STALE"
    );

    private static final List<String> TERMINAL_STATUSES = Arrays.asList(
        "PUBLISHED", "FAILED", "PARTIAL"
    );

    private static final long MAX_STALE_AGE_MS = 24 * 60 * 60 * 1000L;

    @Scheduled(fixedDelayString = "${asm.poll-interval-ms:15000}")
    public void pollJobs() {
        if (!asmProperties.isEnabled()) {
            return;
        }

        List<MarketingJob> jobsToPoll = marketingJobRepository.findByStatusIn(POLLING_STATUSES);
        if (jobsToPoll.isEmpty()) {
            return;
        }

        log.debug("Polling {} marketing jobs", jobsToPoll.size());

        Instant now = Instant.now();

        // Detect and reschedule jobs with expired scheduled publish time
        List<MarketingJob> expiredJobs = marketingJobRepository.findExpiredScheduledJobs();
        if (!expiredJobs.isEmpty()) {
            log.info("Found {} expired scheduled jobs, rescheduling...", expiredJobs.size());
            for (MarketingJob expiredJob : expiredJobs) {
                rescheduleExpiredJob(expiredJob, now);
            }
        }

        for (MarketingJob job : jobsToPoll) {
            if ("STALE".equals(job.getStatus())) {
                // 24h expiry → permanent FAILED
                if (job.getCreatedAt() != null &&
                    now.toEpochMilli() - job.getCreatedAt().toEpochMilli() > MAX_STALE_AGE_MS) {
                    job.setStatus("FAILED");
                    job.setErrorMessage("ASM 응답 24시간 초과 — 자동 실패 처리");
                    marketingJobRepository.save(job);
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
            } catch (AsmUnavailableException e) {
                log.warn("ASM unavailable when polling job {}: {}", job.getId(), e.getMessage());
                job.markPollFailure(e.getMessage());
                marketingJobRepository.save(job);
            }
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
            log.warn("Expired job {} has null scheduledPublishAt, skipping reschedule", job.getId());
            return;
        }

        // Calculate next day at same time
        Instant nextDayTime = originalScheduledAt.plus(1, ChronoUnit.DAYS);
        Instant newScheduledTime = findAvailableSlot(nextDayTime, job.getId());

        // Set original scheduled time on first reschedule
        if (job.getOriginalScheduledAt() == null) {
            job.setOriginalScheduledAt(originalScheduledAt);
        }

        // Update job with new schedule
        job.setScheduledPublishAt(newScheduledTime);
        job.setRescheduledCount(job.getRescheduledCount() + 1);
        job.setLastRescheduledAt(now);
        job.setRescheduledReason("예약 시각 경과 (원 예약: " + originalScheduledAt + ")");

        marketingJobRepository.save(job);

        log.info("Rescheduled expired marketing job {} from {} to {} (reschedule #{}, reason: {})",
            job.getId(), originalScheduledAt, newScheduledTime, job.getRescheduledCount(),
            job.getRescheduledReason());

        telegramNotifier.send(String.format(
            "⚠️ [Again-Spring] 마케팅 예약 발행 이월%n잡 #%d — %s → %s (원 예약 %s, %d회째 이월)",
            job.getId(),
            KST_FORMAT.format(originalScheduledAt),
            KST_FORMAT.format(newScheduledTime),
            KST_FORMAT.format(job.getOriginalScheduledAt()),
            job.getRescheduledCount()));
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
}
