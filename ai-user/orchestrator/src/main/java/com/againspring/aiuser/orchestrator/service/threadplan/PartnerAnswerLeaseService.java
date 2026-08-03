package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPartnerAnswer;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPartnerAnswerStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPartnerAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Lease protocol for {@link PartnerAnswerPublisher} — same split as
 * {@link ScheduledPostLeaseService} so {@code @Transactional} claim works through the proxy.
 */
@Service
@RequiredArgsConstructor
public class PartnerAnswerLeaseService {
    private final AiScheduledPartnerAnswerRepository answers;

    @Transactional
    public List<AiScheduledPartnerAnswer> claimDue(String workerId, int limit, Duration leaseDuration, Instant now) {
        List<AiScheduledPartnerAnswer> due = answers.lockDueItems(
                ScheduledPartnerAnswerStatus.SCHEDULED, now, PageRequest.of(0, Math.max(1, limit)));
        Instant leaseUntil = now.plus(leaseDuration);
        due.forEach(row -> {
            row.setStatus(ScheduledPartnerAnswerStatus.PUBLISHING);
            row.setLeaseOwner(workerId);
            row.setLeaseUntil(leaseUntil);
            row.setAttemptCount(row.getAttemptCount() + 1);
        });
        return due;
    }

    @Transactional
    public void complete(String id, String workerId) {
        AiScheduledPartnerAnswer row = owned(id, workerId);
        row.setStatus(ScheduledPartnerAnswerStatus.COMPLETED);
        row.setLeaseOwner(null);
        row.setLeaseUntil(null);
    }

    @Transactional
    public void releaseFailed(String id, String workerId, String failureCode, boolean retryable) {
        AiScheduledPartnerAnswer row = owned(id, workerId);
        row.setStatus(retryable ? ScheduledPartnerAnswerStatus.SCHEDULED : ScheduledPartnerAnswerStatus.FAILED);
        row.setFailureCode(failureCode);
        row.setLeaseOwner(null);
        row.setLeaseUntil(null);
    }

    private AiScheduledPartnerAnswer owned(String id, String workerId) {
        AiScheduledPartnerAnswer row = answers.findById(id).orElseThrow();
        if (row.getStatus() != ScheduledPartnerAnswerStatus.PUBLISHING || !workerId.equals(row.getLeaseOwner())) {
            throw new IllegalStateException("partner answer lease is not owned by " + workerId);
        }
        return row;
    }
}
