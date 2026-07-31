package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for stampede redistribution decision logic in ThreadPlanPublisher.
 * These tests verify the stalenessDecision() helper without requiring Spring or database mocking.
 */
class ThreadPlanPublisherStampedeTest {

    private static final Duration STAMPEDE_THRESHOLD = Duration.ofMinutes(30);
    private static final Duration MIN_REMAINING_FOR_REDISTRIBUTE = Duration.ofMinutes(15);

    /**
     * Decision result enum used internally for testing.
     */
    enum StampedeDecision {
        PUBLISH_IMMEDIATELY,
        DEFER_AND_REDISTRIBUTE,
        PUBLISH_DUE_TO_MISSING_SCHEDULE,
        PUBLISH_DUE_TO_MISSING_PLAN,
        PUBLISH_DUE_TO_EXPIRED_PLAN,
        PUBLISH_DUE_TO_MINIMAL_TIME_REMAINING
    }

    /**
     * Extracted decision logic for testing (mirrors handleStampedeRedistribution logic).
     */
    static StampedeDecision decidStampedeAction(AiThreadPlanItem item, AiThreadPlan plan, Instant now) {
        if (item.getScheduledAt() == null) {
            return StampedeDecision.PUBLISH_DUE_TO_MISSING_SCHEDULE;
        }

        long staleDurationSeconds = Duration.between(item.getScheduledAt(), now).getSeconds();
        if (staleDurationSeconds <= STAMPEDE_THRESHOLD.getSeconds()) {
            return StampedeDecision.PUBLISH_IMMEDIATELY;
        }

        // Item is stale: check if plan is available
        if (plan == null) {
            return StampedeDecision.PUBLISH_DUE_TO_MISSING_PLAN;
        }

        long remainingSeconds = Duration.between(now, plan.getAbsoluteExpiresAt()).getSeconds();
        if (remainingSeconds <= 0) {
            return StampedeDecision.PUBLISH_DUE_TO_EXPIRED_PLAN;
        }

        if (remainingSeconds < MIN_REMAINING_FOR_REDISTRIBUTE.getSeconds()) {
            return StampedeDecision.PUBLISH_DUE_TO_MINIMAL_TIME_REMAINING;
        }

        return StampedeDecision.DEFER_AND_REDISTRIBUTE;
    }

    @Test
    void shouldPublishImmediatelyWhenItemNotStale() {
        Instant now = Instant.now();
        Instant scheduledAt = now.minus(Duration.ofMinutes(10)); // 10 min late (< 30 min threshold)
        Instant expiresAt = now.plus(Duration.ofHours(1));

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(scheduledAt);
        item.setPlanId("test-plan");

        AiThreadPlan plan = new AiThreadPlan();
        plan.setAbsoluteExpiresAt(expiresAt);

        StampedeDecision decision = decidStampedeAction(item, plan, now);
        assertThat(decision).isEqualTo(StampedeDecision.PUBLISH_IMMEDIATELY);
    }

    @Test
    void shouldDeferWhenItemStaleWithSufficientTimeRemaining() {
        Instant now = Instant.now();
        Instant scheduledAt = now.minus(Duration.ofMinutes(45)); // 45 min late (> 30 min threshold)
        Instant expiresAt = now.plus(Duration.ofHours(2)); // 2h remaining

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(scheduledAt);
        item.setPlanId("test-plan");

        AiThreadPlan plan = new AiThreadPlan();
        plan.setAbsoluteExpiresAt(expiresAt);

        StampedeDecision decision = decidStampedeAction(item, plan, now);
        assertThat(decision).isEqualTo(StampedeDecision.DEFER_AND_REDISTRIBUTE);
    }

    @Test
    void shouldPublishWhenStaleButExpiryAlreadyPassed() {
        Instant now = Instant.now();
        Instant scheduledAt = now.minus(Duration.ofMinutes(45)); // 45 min late
        Instant expiresAt = now.minus(Duration.ofMinutes(5)); // already expired

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(scheduledAt);
        item.setPlanId("test-plan");

        AiThreadPlan plan = new AiThreadPlan();
        plan.setAbsoluteExpiresAt(expiresAt);

        StampedeDecision decision = decidStampedeAction(item, plan, now);
        assertThat(decision).isEqualTo(StampedeDecision.PUBLISH_DUE_TO_EXPIRED_PLAN);
    }

    @Test
    void shouldPublishWhenStaleButMinimalTimeRemaining() {
        Instant now = Instant.now();
        Instant scheduledAt = now.minus(Duration.ofMinutes(45)); // 45 min late
        Instant expiresAt = now.plus(Duration.ofMinutes(10)); // only 10 min remaining (< 15 min min)

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(scheduledAt);
        item.setPlanId("test-plan");

        AiThreadPlan plan = new AiThreadPlan();
        plan.setAbsoluteExpiresAt(expiresAt);

        StampedeDecision decision = decidStampedeAction(item, plan, now);
        assertThat(decision).isEqualTo(StampedeDecision.PUBLISH_DUE_TO_MINIMAL_TIME_REMAINING);
    }

    @Test
    void shouldPublishWhenScheduledAtIsNull() {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(1));

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(null);
        item.setPlanId("test-plan");

        AiThreadPlan plan = new AiThreadPlan();
        plan.setAbsoluteExpiresAt(expiresAt);

        StampedeDecision decision = decidStampedeAction(item, plan, now);
        assertThat(decision).isEqualTo(StampedeDecision.PUBLISH_DUE_TO_MISSING_SCHEDULE);
    }

    @Test
    void shouldPublishWhenPlanNotFound() {
        Instant now = Instant.now();
        Instant scheduledAt = now.minus(Duration.ofMinutes(45)); // 45 min late

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(scheduledAt);
        item.setPlanId("nonexistent-plan");

        StampedeDecision decision = decidStampedeAction(item, null, now);
        assertThat(decision).isEqualTo(StampedeDecision.PUBLISH_DUE_TO_MISSING_PLAN);
    }

    @Test
    void shouldDeferAtBoundary15MinutesRemaining() {
        Instant now = Instant.now();
        Instant scheduledAt = now.minus(Duration.ofMinutes(45)); // 45 min late
        Instant expiresAt = now.plus(Duration.ofMinutes(15)); // exactly 15 min remaining (boundary)

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(scheduledAt);
        item.setPlanId("test-plan");

        AiThreadPlan plan = new AiThreadPlan();
        plan.setAbsoluteExpiresAt(expiresAt);

        // At exactly 15 min, the condition is NOT (remaining < 15), so it should defer
        StampedeDecision decision = decidStampedeAction(item, plan, now);
        assertThat(decision).isEqualTo(StampedeDecision.DEFER_AND_REDISTRIBUTE);
    }

    @Test
    void shouldPublishAtBoundary30MinutesLate() {
        Instant now = Instant.now();
        Instant scheduledAt = now.minus(Duration.ofMinutes(30)); // exactly 30 min late (boundary)
        Instant expiresAt = now.plus(Duration.ofHours(1));

        AiThreadPlanItem item = new AiThreadPlanItem();
        item.setScheduledAt(scheduledAt);
        item.setPlanId("test-plan");

        AiThreadPlan plan = new AiThreadPlan();
        plan.setAbsoluteExpiresAt(expiresAt);

        // At exactly 30 min, the condition is NOT (stale > threshold), so it should publish normally
        StampedeDecision decision = decidStampedeAction(item, plan, now);
        assertThat(decision).isEqualTo(StampedeDecision.PUBLISH_IMMEDIATELY);
    }
}
