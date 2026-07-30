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
        AiHumanInteractionInbox entry = owned(inboxId, workerId);
        entry.setStatus(HumanInteractionStatus.RESPONDED);
        entry.setResponseItemId(responseItemId);
        entry.setLeaseOwner(null);
        entry.setLeaseUntil(null);
    }

    @Transactional
    public void release(String inboxId, String workerId) {
        AiHumanInteractionInbox entry = owned(inboxId, workerId);
        entry.setStatus(HumanInteractionStatus.PENDING);
        entry.setLeaseOwner(null); entry.setLeaseUntil(null);
    }

    @Transactional
    public int recoverAndExpire(Instant now) {
        int recovered = inboxRepository.recoverExpiredLeases(HumanInteractionStatus.PROCESSING,
                HumanInteractionStatus.PENDING, now);
        return recovered + inboxRepository.expirePastDue(HumanInteractionStatus.PENDING,
                HumanInteractionStatus.PROCESSING, HumanInteractionStatus.EXPIRED, now);
    }

    private AiHumanInteractionInbox owned(String inboxId, String workerId) {
        AiHumanInteractionInbox entry = inboxRepository.findById(inboxId).orElseThrow();
        if (entry.getStatus() != HumanInteractionStatus.PROCESSING || !workerId.equals(entry.getLeaseOwner())) {
            throw new IllegalStateException("human interaction lease is not owned by worker");
        }
        return entry;
    }
}
