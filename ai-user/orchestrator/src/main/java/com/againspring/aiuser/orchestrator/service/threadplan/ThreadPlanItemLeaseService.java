package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Database lease protocol for a future publisher. Claiming is separate from publishing. */
@Service
@RequiredArgsConstructor
public class ThreadPlanItemLeaseService {
    private final AiThreadPlanItemRepository itemRepository;

    @Transactional
    public List<AiThreadPlanItem> claimDue(String workerId, int limit, Duration leaseDuration, Instant now) {
        List<AiThreadPlanItem> items = itemRepository.lockDueItems(
                ThreadPlanItemStatus.SCHEDULED, now, PageRequest.of(0, Math.max(1, limit)));
        Instant leaseUntil = now.plus(leaseDuration);
        items.forEach(item -> {
            item.setStatus(ThreadPlanItemStatus.PROCESSING);
            item.setLeaseOwner(workerId);
            item.setLeaseUntil(leaseUntil);
            item.setAttemptCount(item.getAttemptCount() + 1);
        });
        return items;
    }

    @Transactional
    public void completePosted(String itemId, String workerId, String postedTargetId) {
        AiThreadPlanItem item = owned(itemId, workerId);
        item.setStatus(ThreadPlanItemStatus.POSTED);
        item.setPostedTargetId(postedTargetId);
        item.setLeaseOwner(null);
        item.setLeaseUntil(null);
    }

    @Transactional
    public void releaseFailed(String itemId, String workerId, String failureCode, boolean retryable) {
        AiThreadPlanItem item = owned(itemId, workerId);
        item.setStatus(retryable ? ThreadPlanItemStatus.SCHEDULED : ThreadPlanItemStatus.FAILED);
        item.setFailureCode(failureCode);
        item.setLeaseOwner(null);
        item.setLeaseUntil(null);
    }

    @Transactional
    public void defer(String itemId, String workerId, Instant scheduledAt, String reason) {
        AiThreadPlanItem item = owned(itemId, workerId);
        item.setStatus(ThreadPlanItemStatus.SCHEDULED);
        item.setScheduledAt(scheduledAt);
        item.setNotBefore(scheduledAt);
        item.setFailureCode(reason);
        item.setLeaseOwner(null); item.setLeaseUntil(null);
    }

    @Transactional
    public int recoverExpiredLeases(Instant now) {
        return itemRepository.recoverExpiredLeases(ThreadPlanItemStatus.PROCESSING,
                ThreadPlanItemStatus.SCHEDULED, now);
    }

    private AiThreadPlanItem owned(String itemId, String workerId) {
        AiThreadPlanItem item = itemRepository.findById(itemId).orElseThrow();
        if (item.getStatus() != ThreadPlanItemStatus.PROCESSING || !workerId.equals(item.getLeaseOwner())) {
            throw new IllegalStateException("thread plan item lease is not owned by worker");
        }
        return item;
    }
}
