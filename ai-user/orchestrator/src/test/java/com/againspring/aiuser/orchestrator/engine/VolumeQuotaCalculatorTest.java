package com.againspring.aiuser.orchestrator.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VolumeQuotaCalculator 확률적 반올림 검증.
 * 핵심 회귀: 매분 틱(1440/일)·cap 1000에서 야간(weight 0.111) budget이
 * round()로 항상 0이 되던 버그 — 9시간 침묵(KST 23~08시)을 막는다.
 */
class VolumeQuotaCalculatorTest {

    /** nextRandom을 고정값으로 제어해 결정적으로 검증. */
    static class FixedRandomCalc extends VolumeQuotaCalculator {
        private final double fixed;
        FixedRandomCalc(double fixed) { this.fixed = fixed; }
        @Override protected double nextRandom() { return fixed; }
    }

    @Test
    void nightWeight_canProduceAction() {
        // cap=1000, 1440 ticks → basePerTick=0.694, 야간 weight=0.111
        // expected = 0.694 * 0.111 * 2 ≈ 0.154
        // rand=0.0 < 0.154 → 1 (기존 round()는 항상 0이었음 — 회귀 방지)
        assertEquals(1, new FixedRandomCalc(0.0).calculate(1000, 1440, 0.111, 1000));
        // rand=0.9 ≥ 0.154 → 0 (야간은 대부분 침묵하되 일부 틱만 활동)
        assertEquals(0, new FixedRandomCalc(0.9).calculate(1000, 1440, 0.111, 1000));
    }

    @Test
    void peakWeight_producesActions() {
        // expected = 0.694 * 1.0 * 2 ≈ 1.389 → floor 1 + (rand<0.389?1:0)
        assertEquals(2, new FixedRandomCalc(0.0).calculate(1000, 1440, 1.0, 1000));
        assertEquals(1, new FixedRandomCalc(0.9).calculate(1000, 1440, 1.0, 1000));
    }

    @Test
    void respectsRemainingCap() {
        // expected 1.389 → 2 이지만 remaining=1로 클램프
        assertEquals(1, new FixedRandomCalc(0.0).calculate(1000, 1440, 1.0, 1));
    }

    @Test
    void zeroRemaining_returnsZero() {
        assertEquals(0, new VolumeQuotaCalculator().calculate(1000, 1440, 1.0, 0));
    }

    @Test
    void integerExpected_isDeterministic() {
        // frac=0 → 난수와 무관하게 그대로 (rand < 0.0 은 항상 거짓)
        VolumeQuotaCalculator c = new VolumeQuotaCalculator();
        assertEquals(0, c.stochasticRound(0.0));
        assertEquals(3, c.stochasticRound(3.0));
    }

    @Test
    void expectedValuePreserved_overManyTicks() {
        // 실제 Math.random으로 야간 expected≈0.154를 1만 틱 누적 → 평균이 기대값 근처
        VolumeQuotaCalculator c = new VolumeQuotaCalculator();
        int total = 0;
        int ticks = 10000;
        for (int i = 0; i < ticks; i++) {
            total += c.calculate(1000, 1440, 0.111, 1000);
        }
        double avg = (double) total / ticks;
        // 기대값 0.154, 넉넉한 허용범위로 flakiness 방지
        assertTrue(avg > 0.10 && avg < 0.21, "avg=" + avg + " (expected ≈0.154)");
        // 야간에도 누적 활동이 0이 아님을 보장 (회귀 핵심)
        assertTrue(total > 0, "night ticks must accumulate some actions");
    }

    @Test
    void circadianWeight_normalizesToPeak() {
        VolumeQuotaCalculator c = new VolumeQuotaCalculator();
        // 기본 커브 피크(20시)=0.9 → 정규화 1.0, 야간(0시)=0.1 → 0.111
        assertEquals(1.0, c.circadianWeight(20, null), 1e-9);
        assertEquals(0.1 / 0.9, c.circadianWeight(0, null), 1e-9);
    }
}
