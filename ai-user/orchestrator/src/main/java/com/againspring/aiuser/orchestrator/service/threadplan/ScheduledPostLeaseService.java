package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Database lease protocol for {@link ScheduledPostPublisher}, split into its own bean for the
 * same reason {@link ThreadPlanItemLeaseService} is separate from {@link ThreadPlanPublisher}:
 * {@code @Transactional} on a method only takes effect through Spring's proxy, and a method
 * calling another method on {@code this} never goes through the proxy — self-invocation silently
 * skips the transaction (confirmed 2026-07-31: {@code lockDueItems}'s PESSIMISTIC_WRITE lock
 * threw "no transaction in progress" every tick when claimDue lived inside ScheduledPostPublisher
 * itself). Claiming is a separate call through this bean; publishing stays untransactional so an
 * LLM-free HTTP round trip to backend never holds a DB lock.
 */
@Service
@RequiredArgsConstructor
public class ScheduledPostLeaseService {
    private final AiScheduledPostRepository scheduledPosts;

    @Transactional
    public List<AiScheduledPost> claimDue(String workerId, int limit, Duration leaseDuration, Instant now) {
        List<AiScheduledPost> due = scheduledPosts.lockDueItems(ScheduledPostStatus.SCHEDULED, now,
                PageRequest.of(0, Math.max(1, limit)));
        Instant leaseUntil = now.plus(leaseDuration);
        due.forEach(row -> {
            row.setStatus(ScheduledPostStatus.PUBLISHING);
            row.setLeaseOwner(workerId);
            row.setLeaseUntil(leaseUntil);
            row.setAttemptCount(row.getAttemptCount() + 1);
        });
        return due;
    }

    /**
     * Claim a single row by id for {@link ScheduledPostPublisher#publishNow} — dev canary only.
     * Only claims rows still {@code SCHEDULED} (not already publishing/published/failed).
     */
    @Transactional
    public Optional<AiScheduledPost> claimById(String id, String workerId, Duration leaseDuration) {
        Optional<AiScheduledPost> found = scheduledPosts.findById(id);
        if (found.isEmpty()) return Optional.empty();
        AiScheduledPost row = found.get();
        if (row.getStatus() != ScheduledPostStatus.SCHEDULED) return Optional.empty();
        row.setStatus(ScheduledPostStatus.PUBLISHING);
        row.setLeaseOwner(workerId);
        row.setLeaseUntil(Instant.now().plus(leaseDuration));
        row.setAttemptCount(row.getAttemptCount() + 1);
        return Optional.of(row);
    }

    /** Undo a {@link #claimById} lease (e.g. force=false and the slot isn't due yet) without failing the row. */
    @Transactional
    public void release(String id, String workerId) {
        AiScheduledPost row = owned(id, workerId);
        row.setStatus(ScheduledPostStatus.SCHEDULED);
        row.setLeaseOwner(null);
        row.setLeaseUntil(null);
        row.setAttemptCount(Math.max(0, row.getAttemptCount() - 1));
    }

    @Transactional
    public void completePosted(String id, String workerId, String postId) {
        AiScheduledPost row = owned(id, workerId);
        row.setStatus(ScheduledPostStatus.PUBLISHED);
        row.setPublishedPostId(postId);
        row.setPublishedAt(Instant.now());
        row.setLeaseOwner(null);
        row.setLeaseUntil(null);
    }

    /**
     * Returns a claimed row to SCHEDULED with a later slot (e.g. quiet-hour defer).
     * Does not increment attempt_count again on the next claim.
     */
    @Transactional
    public void defer(String id, String workerId, Instant newScheduledAt, String reason) {
        AiScheduledPost row = owned(id, workerId);
        row.setStatus(ScheduledPostStatus.SCHEDULED);
        row.setScheduledPublishAt(newScheduledAt);
        row.setFailureCode(reason);
        row.setLeaseOwner(null);
        row.setLeaseUntil(null);
        // Undo the claim's attempt bump so quiet-hour deferrals don't burn retries.
        row.setAttemptCount(Math.max(0, row.getAttemptCount() - 1));
    }

    @Transactional
    public void releaseFailed(String id, String workerId, String failureCode, boolean retryable) {
        AiScheduledPost row = owned(id, workerId);
        row.setStatus(retryable ? ScheduledPostStatus.SCHEDULED : ScheduledPostStatus.FAILED);
        row.setFailureCode(failureCode);
        row.setLeaseOwner(null);
        row.setLeaseUntil(null);
    }

    private AiScheduledPost owned(String id, String workerId) {
        AiScheduledPost row = scheduledPosts.findById(id).orElseThrow();
        if (row.getStatus() != ScheduledPostStatus.PUBLISHING || !workerId.equals(row.getLeaseOwner())) {
            throw new IllegalStateException("scheduled post lease is not owned by " + workerId);
        }
        return row;
    }
}
