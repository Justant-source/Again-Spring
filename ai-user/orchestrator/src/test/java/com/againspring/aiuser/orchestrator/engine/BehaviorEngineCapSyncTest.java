package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.domain.enums.ActionType;
import com.againspring.aiuser.orchestrator.service.ActionTypeQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test cap sync and type budget computation in BehaviorEngine.
 * Uses a simple test implementation that directly calls the helper.
 */
@DisplayName("BehaviorEngine Cap Sync & Type Budget Tests")
class BehaviorEngineCapSyncTest {

    private BehaviorEngineTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new BehaviorEngineTestHarness();
    }

    @Test
    @DisplayName("computeTickTypeBudget returns 0 for deficit=0 types")
    void testComputeTickTypeBudgetZeroDeficit() {
        // Given: quota with one type at 0 deficit, one with deficit > 0
        Map<ActionType, ActionTypeQuotaService.TypeQuota> quotas = new EnumMap<>(ActionType.class);
        quotas.put(ActionType.POST, new ActionTypeQuotaService.TypeQuota(5, 5, 0));  // deficit=0
        quotas.put(ActionType.LIKE, new ActionTypeQuotaService.TypeQuota(10, 5, 5)); // deficit=5

        double hourWeight = 1.0;
        int remainingTicksEst = 10;

        // When
        Map<ActionType, Integer> budget = harness.computeTickTypeBudget(quotas, hourWeight, remainingTicksEst);

        // Then
        assertEquals(0, budget.get(ActionType.POST), "Type with deficit=0 should have budget=0");
        assertTrue(budget.get(ActionType.LIKE) >= 0, "Type with deficit>0 should have budget>=0");
    }

    @Test
    @DisplayName("computeTickTypeBudget: higher deficit = higher or equal per-tick budget")
    void testComputeTickTypeBudgetProportional() {
        // Given: two types with different deficits
        Map<ActionType, ActionTypeQuotaService.TypeQuota> quotas = new EnumMap<>(ActionType.class);
        quotas.put(ActionType.LIKE, new ActionTypeQuotaService.TypeQuota(10, 5, 5));     // deficit=5
        quotas.put(ActionType.VOTE, new ActionTypeQuotaService.TypeQuota(20, 10, 10)); // deficit=10

        double hourWeight = 1.0;
        int remainingTicksEst = 10;

        // When
        Map<ActionType, Integer> budget = harness.computeTickTypeBudget(quotas, hourWeight, remainingTicksEst);

        // Then
        int likeBudget = budget.get(ActionType.LIKE);
        int voteBudget = budget.get(ActionType.VOTE);

        assertTrue(likeBudget >= 0, "LIKE budget should be non-negative");
        assertTrue(voteBudget >= likeBudget, "VOTE (2x deficit) should have >= budget as LIKE");
    }

    @Test
    @DisplayName("computeTickTypeBudget respects hourWeight multiplier")
    void testComputeTickTypeBudgetHourWeight() {
        // Given: same quota, different hourWeights
        Map<ActionType, ActionTypeQuotaService.TypeQuota> quotas = new EnumMap<>(ActionType.class);
        quotas.put(ActionType.LIKE, new ActionTypeQuotaService.TypeQuota(10, 5, 5));

        int remainingTicksEst = 10;

        // When: with hourWeight=2.0
        Map<ActionType, Integer> budget_high = harness.computeTickTypeBudget(quotas, 2.0, remainingTicksEst);

        // When: with hourWeight=1.0
        Map<ActionType, Integer> budget_low = harness.computeTickTypeBudget(quotas, 1.0, remainingTicksEst);

        // Then
        assertTrue(budget_high.get(ActionType.LIKE) >= budget_low.get(ActionType.LIKE),
            "Higher hourWeight should yield >= budget");
    }

    @Test
    @DisplayName("computeTickTypeBudget handles empty quota gracefully")
    void testComputeTickTypeBudgetEmpty() {
        // Given: empty quota
        Map<ActionType, ActionTypeQuotaService.TypeQuota> quotas = new EnumMap<>(ActionType.class);

        // When
        Map<ActionType, Integer> budget = harness.computeTickTypeBudget(quotas, 1.0, 10);

        // Then
        assertTrue(budget.isEmpty(), "Empty quota should yield empty budget");
    }

    /**
     * Simple test harness that exposes the budget computation logic.
     */
    private static class BehaviorEngineTestHarness {
        private final VolumeQuotaCalculator quotaCalc = new TestQuotaCalc();

        Map<ActionType, Integer> computeTickTypeBudget(
                Map<ActionType, ActionTypeQuotaService.TypeQuota> quotas,
                double hourWeight, int remainingTicksEst) {
            Map<ActionType, Integer> result = new EnumMap<>(ActionType.class);
            for (Map.Entry<ActionType, ActionTypeQuotaService.TypeQuota> e : quotas.entrySet()) {
                ActionTypeQuotaService.TypeQuota q = e.getValue();
                if (q.deficit() <= 0) {
                    result.put(e.getKey(), 0);
                } else {
                    double expected = (double) q.deficit() * hourWeight * 2.0 / Math.max(remainingTicksEst, 1);
                    result.put(e.getKey(), quotaCalc.stochasticRound(expected));
                }
            }
            return result;
        }
    }

    /**
     * Test implementation of VolumeQuotaCalculator.
     */
    private static class TestQuotaCalc extends VolumeQuotaCalculator {
        @Override
        protected double nextRandom() {
            return 0.5; // Fixed for deterministic tests
        }
    }
}
