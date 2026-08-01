package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiHumanInteractionInbox;
import com.againspring.aiuser.orchestrator.domain.enums.HumanInteractionStatus;
import com.againspring.aiuser.orchestrator.repository.AiHumanInteractionInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Durable input boundary for the 30-minute human-response batch; no LLM invocation lives here. */
@Service
@RequiredArgsConstructor
public class HumanInteractionInboxService {
    public static final String REASON_EXPIRED_TTL = "EXPIRED_TTL";

    private final AiHumanInteractionInboxRepository inboxRepository;

    @Transactional
    public AiHumanInteractionInbox observe(String postId, String sourceCommentId, String parentCommentId,
                                            String authorId, String interactionType, Instant observedAt,
                                            Instant expiresAt) {
        return inboxRepository.findBySourceCommentId(sourceCommentId).orElseGet(() -> {
            AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                    .postId(postId).sourceCommentId(sourceCommentId).parentCommentId(parentCommentId)
                    .authorId(authorId).interactionType(interactionType).observedAt(observedAt)
                    .expiresAt(expiresAt).status(HumanInteractionStatus.PENDING).build();
            try {
                return inboxRepository.saveAndFlush(entry);
            } catch (DataIntegrityViolationException duplicateDelivery) {
                return inboxRepository.findBySourceCommentId(sourceCommentId).orElseThrow(() -> duplicateDelivery);
            }
        });
    }

    @Transactional
    public List<AiHumanInteractionInbox> claimPending(String workerId, int limit, Duration leaseDuration, Instant now) {
        List<AiHumanInteractionInbox> entries = inboxRepository.lockPendingForBatch(
                HumanInteractionStatus.PENDING, now, PageRequest.of(0, Math.max(1, limit)));
        Instant leaseUntil = now.plus(leaseDuration);
        entries.forEach(entry -> {
            entry.setStatus(HumanInteractionStatus.PROCESSING);
            entry.setLeaseOwner(workerId);
            entry.setLeaseUntil(leaseUntil);
        });
        return entries;
    }

    @Transactional
    public void markResponded(String inboxId, String workerId, String responseItemId) {
        markResponded(inboxId, workerId, responseItemId, null);
    }

    /** Success path: clear last_error_code; optionally record automatic attempt_count. */
    @Transactional
    public void markResponded(String inboxId, String workerId, String responseItemId, Integer attemptCount) {
        AiHumanInteractionInbox entry = owned(inboxId, workerId);
        entry.setStatus(HumanInteractionStatus.RESPONDED);
        entry.setResponseItemId(responseItemId);
        entry.setFailureCode(null);
        entry.setLastErrorCode(null);
        if (attemptCount != null) entry.setAttemptCount(Math.max(0, attemptCount));
        entry.setLeaseOwner(null);
        entry.setLeaseUntil(null);
    }

    @Transactional
    public void markSkipped(String inboxId, String workerId, String failureCode) {
        markSkipped(inboxId, workerId, failureCode, null);
    }

    /**
     * Terminal skip/fail with a safe failure code (never LLM error text).
     * When {@code attemptCount} is set, persists the automatic retry ledger.
     */
    @Transactional
    public void markSkipped(String inboxId, String workerId, String failureCode, Integer attemptCount) {
        AiHumanInteractionInbox entry = owned(inboxId, workerId);
        entry.setStatus(HumanInteractionStatus.SKIPPED);
        entry.setFailureCode(failureCode);
        entry.setLastErrorCode(failureCode);
        if (attemptCount != null) entry.setAttemptCount(Math.max(0, attemptCount));
        entry.setLeaseOwner(null);
        entry.setLeaseUntil(null);
    }

    @Transactional
    public void release(String inboxId, String workerId) {
        AiHumanInteractionInbox entry = owned(inboxId, workerId);
        entry.setStatus(HumanInteractionStatus.PENDING);
        entry.setLeaseOwner(null); entry.setLeaseUntil(null);
    }

    /** Recover lease-expired PROCESSING rows and expire rows past expires_at. */
    @Transactional
    public int recoverAndExpire(Instant now) {
        int recovered = inboxRepository.recoverExpiredLeases(HumanInteractionStatus.PROCESSING,
                HumanInteractionStatus.PENDING, now);
        return recovered + inboxRepository.expirePastDue(HumanInteractionStatus.PENDING,
                HumanInteractionStatus.PROCESSING, HumanInteractionStatus.EXPIRED, now);
    }

    /** Force every PROCESSING lease back to PENDING so the batch can re-claim them. */
    @Transactional
    public int reclaimStuckProcessing() {
        return inboxRepository.reclaimAllProcessing(
                HumanInteractionStatus.PROCESSING, HumanInteractionStatus.PENDING);
    }

    /**
     * State-transition cancel for rows whose observed_at (detected_at) is older than {@code cutoff}.
     * Does not delete rows; records {@link #REASON_EXPIRED_TTL}.
     */
    @Transactional
    public int cancelExpiredByObservedAt(Instant cutoff) {
        return inboxRepository.cancelOlderThan(
                HumanInteractionStatus.PENDING,
                HumanInteractionStatus.PROCESSING,
                HumanInteractionStatus.CANCELLED,
                REASON_EXPIRED_TTL,
                cutoff);
    }

    private AiHumanInteractionInbox owned(String inboxId, String workerId) {
        AiHumanInteractionInbox entry = inboxRepository.findById(inboxId).orElseThrow();
        if (entry.getStatus() != HumanInteractionStatus.PROCESSING || !workerId.equals(entry.getLeaseOwner())) {
            throw new IllegalStateException("human interaction lease is not owned by worker");
        }
        return entry;
    }
}
