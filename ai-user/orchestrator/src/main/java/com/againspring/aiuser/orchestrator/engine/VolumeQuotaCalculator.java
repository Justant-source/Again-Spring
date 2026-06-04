package com.againspring.aiuser.orchestrator.engine;

import org.springframework.stereotype.Component;

/**
 * 이번 tick에 실행할 행동 수를 산정.
 * top-down: daily_global_cap × circadian_weight → tick_budget
 * LLM 미사용 — 순수 계산.
 */
@Component
public class VolumeQuotaCalculator {

    /**
     * @param dailyGlobalCap   일일 전체 행동 상한
     * @param ticksPerDay      하루 예상 tick 수 (24h/tick interval)
     * @param hourWeightNorm   circadian 정규화 가중치 (0~1)
     * @param remainingToday   오늘 남은 행동 할당량
     * @return 이번 tick 행동 목표 수 (0~remainingToday)
     */
    public int calculate(int dailyGlobalCap, int ticksPerDay, double hourWeightNorm, int remainingToday) {
        if (remainingToday <= 0) return 0;
        double basePerTick = (double) dailyGlobalCap / Math.max(ticksPerDay, 1);
        // Apply circadian weight (0 = night silence, 1 = peak activity)
        // Multiply by 2 to allow peaks to compensate for off-peak silence
        int tickBudget = (int) Math.round(basePerTick * hourWeightNorm * 2.0);
        return Math.max(0, Math.min(tickBudget, remainingToday));
    }

    /**
     * Compute normalized circadian weight for the given hour (KST 0-23).
     * Uses a 24-element activity array where each value is the raw weight.
     * Returns value in [0.0, 1.0].
     */
    public double circadianWeight(int hour, double[] globalCurve) {
        if (globalCurve == null || globalCurve.length < 24) {
            // Default: quiet at night, active evening
            double[] defaultCurve = {0.0,0.0,0.0,0.0,0.0,0.0,0.1,0.2,0.4,0.5,0.5,0.5,
                                     0.4,0.4,0.4,0.5,0.5,0.6,0.7,0.8,0.9,0.8,0.6,0.2};
            double max = 0.9;
            return defaultCurve[Math.min(hour, 23)] / max;
        }
        double max = 0.0;
        for (double v : globalCurve) max = Math.max(max, v);
        if (max == 0.0) return 0.0;
        return globalCurve[Math.min(hour, 23)] / max;
    }

    /** Estimate ticks per day from cron expression (simple parsing). */
    public int estimateTicksPerDay(String cronExpr) {
        if (cronExpr == null) return 144; // default 10-min ticks
        try {
            // Expect "0 */N * * * *" pattern — extract N
            String[] parts = cronExpr.trim().split("\\s+");
            if (parts.length >= 2) {
                String minPart = parts[1];
                if (minPart.startsWith("*/")) {
                    int interval = Integer.parseInt(minPart.substring(2));
                    return 60 / interval * 24;
                }
            }
        } catch (Exception ignored) {}
        return 144;
    }
}
