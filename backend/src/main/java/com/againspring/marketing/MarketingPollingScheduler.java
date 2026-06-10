package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
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
}
