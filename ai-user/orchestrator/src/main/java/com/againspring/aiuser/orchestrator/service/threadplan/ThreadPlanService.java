package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;

/** Revision-safe persistence operations. It never calls an LLM or posts content. */
@Service
@RequiredArgsConstructor
public class ThreadPlanService {
    private static final EnumSet<ThreadPlanStatus> REPLACEABLE = EnumSet.of(
            ThreadPlanStatus.REQUESTED, ThreadPlanStatus.GENERATING, ThreadPlanStatus.READY,
            ThreadPlanStatus.ACTIVE, ThreadPlanStatus.PAUSED, ThreadPlanStatus.FAILED);
    private static final EnumSet<ThreadPlanItemStatus> UNFINISHED = EnumSet.of(
            ThreadPlanItemStatus.RESERVED, ThreadPlanItemStatus.SCHEDULED,
            ThreadPlanItemStatus.PROCESSING, ThreadPlanItemStatus.FAILED);

    private final AiThreadPlanRepository planRepository;
    private final AiThreadPlanItemRepository itemRepository;

    /**
     * Creates a plan once for a post revision and cancels unpublished work from older revisions.
     * The unique database key is the final idempotency boundary when duplicate outbox delivery races.
     */
    @Transactional
    public AiThreadPlan requestPlan(String postId, int revision, String sourceType,
                                    Instant publishedAt, Instant absoluteExpiresAt) {
        return planRepository.findByPostIdAndPostRevision(postId, revision).orElseGet(() -> {
            planRepository.findByPostIdAndPostRevisionLessThanAndStatusIn(postId, revision, REPLACEABLE)
                    .forEach(previous -> itemRepository.cancelUnfinishedByPlanId(
                            previous.getId(), ThreadPlanItemStatus.CANCELLED, UNFINISHED));
            planRepository.cancelOlderActivePlans(postId, revision, ThreadPlanStatus.CANCELLED, REPLACEABLE);
            // Bulk updates bypass entity callbacks, but this is intentionally only a state transition.
            AiThreadPlan plan = AiThreadPlan.builder()
                    .postId(postId)
                    .postRevision(revision)
                    .sourceType(sourceType)
                    .publishedAt(publishedAt)
                    .absoluteExpiresAt(absoluteExpiresAt)
                    .status(ThreadPlanStatus.REQUESTED)
                    .build();
            try {
                return planRepository.saveAndFlush(plan);
            } catch (DataIntegrityViolationException duplicateDelivery) {
                return planRepository.findByPostIdAndPostRevision(postId, revision)
                        .orElseThrow(() -> duplicateDelivery);
            }
        });
    }

    @Transactional
    public AiThreadPlan requestPlan(String postId, int revision, String sourceType, Instant publishedAt,
                                    String title, String body, String category) {
        AiThreadPlan plan = requestPlan(postId, revision, sourceType, publishedAt, publishedAt.plusSeconds(24 * 3600));
        // The first delivery owns the snapshot. Duplicate deliveries must not overwrite the revision source.
        if (plan.getSourceBody() == null) {
            plan.setSourceTitle(title);
            plan.setSourceBody(body);
            plan.setSourceCategory(category);
        }
        return plan;
    }

    /**
     * Atomically reserves a plan whose content was generated before the post was published.
     * Keeping it out of REQUESTED closes the scheduler race that could otherwise invoke a
     * second generation between POST_PUBLISHED outbox delivery and candidate persistence.
     */
    @Transactional
    public AiThreadPlan reservePreGeneratedBundle(String postId, int revision, Instant publishedAt,
                                                   String title, String body, String category,
                                                   String provider, String model) {
        AiThreadPlan plan = requestPlan(postId, revision, "AI_POST", publishedAt, title, body, category);
        if (plan.getStatus() == ThreadPlanStatus.REQUESTED) {
            plan.setProvider(provider);
            plan.setModel(model);
            plan.setStatus(ThreadPlanStatus.GENERATING);
            plan.setGenerationAttempts(1);
        }
        return plan;
    }

    @Transactional
    public void cancelPlanAndUnpublishedItems(String planId) {
        planRepository.findById(planId).ifPresent(plan -> {
            plan.setStatus(ThreadPlanStatus.CANCELLED);
            itemRepository.cancelUnfinishedByPlanId(planId, ThreadPlanItemStatus.CANCELLED, UNFINISHED);
        });
    }

    @Transactional
    public void cancelPlanAndUnpublishedItemsForPost(String postId) {
        planRepository.findAll().stream().filter(p -> postId.equals(p.getPostId()))
                .forEach(p -> cancelPlanAndUnpublishedItems(p.getId()));
    }

    @Transactional
    public void markGenerating(String planId, String provider, String model) {
        AiThreadPlan plan = planRepository.findById(planId).orElseThrow();
        plan.setProvider(provider);
        plan.setModel(model);
        plan.setStatus(ThreadPlanStatus.GENERATING);
        plan.setGenerationAttempts(plan.getGenerationAttempts() + 1);
    }

    @Transactional
    public void markReady(String planId) {
        AiThreadPlan plan = planRepository.findById(planId).orElseThrow();
        plan.setStatus(ThreadPlanStatus.READY);
        plan.setFailureCode(null);
    }

    @Transactional
    public void activate(String planId) {
        AiThreadPlan plan = planRepository.findById(planId).orElseThrow();
        plan.setStatus(ThreadPlanStatus.ACTIVE);
    }

    @Transactional
    public void markFailed(String planId, String failureCode) {
        AiThreadPlan plan = planRepository.findById(planId).orElseThrow();
        plan.setStatus(ThreadPlanStatus.FAILED);
        plan.setFailureCode(failureCode);
    }
}
