package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        "QUEUED", "RUNNING", "READY", "PUBLISHING"
    );

    private static final List<String> TERMINAL_STATUSES = Arrays.asList(
        "PUBLISHED", "FAILED", "STALE"
    );

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

        for (MarketingJob job : jobsToPoll) {
            try {
                AsmJobView view = asmClient.getJob(job.getRemoteJobId());
                marketingJobService.applyPoll(job, view);
                log.debug("Polled job {} -> status: {}", job.getId(), view.getStatus());
            } catch (AsmUnavailableException e) {
                log.warn("ASM unavailable when polling job {}: {}", job.getId(), e.getMessage());
                job.markPollFailure();
                marketingJobRepository.save(job);
            }
        }
    }
}
