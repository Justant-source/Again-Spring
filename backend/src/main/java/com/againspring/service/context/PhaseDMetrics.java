package com.againspring.service.context;

import com.againspring.domain.Session;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Phase D PR-6 — Micrometer 메트릭.
 * - phase_d.state.{state_name} (Counter): UserState 전환 분포
 * - phase_d.isolation.violations (Counter): IsolationLintFilter 트리거 횟수
 * - phase_d.meta.populated (Counter): turn_meta Phase D 신규 필드가 채워진 응답 수
 * - phase_d.queue.asked (Counter): PQ 항목 발화 횟수 (ask_rate 계산용)
 *
 * 권위본: backend/docs/implementation/phase-d-implementation-instructions.md §6.2
 */
@Component
public class PhaseDMetrics {

    private final MeterRegistry registry;
    private final Counter isolationViolations;
    private final Counter metaPopulated;
    private final Counter queueAsked;

    public PhaseDMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.isolationViolations = Counter.builder("phase_d.isolation.violations")
            .description("IsolationLintFilter 격리 위반 감지 횟수")
            .register(registry);
        this.metaPopulated = Counter.builder("phase_d.meta.populated")
            .description("turn_meta Phase D 신규 필드(userState 또는 issueDelta)가 채워진 응답 수")
            .register(registry);
        this.queueAsked = Counter.builder("phase_d.queue.asked")
            .description("PQ 항목이 LLM에 의해 발화된 횟수")
            .register(registry);
    }

    public void recordIsolationViolation() {
        isolationViolations.increment();
    }

    public void recordMetaPopulated() {
        metaPopulated.increment();
    }

    public void recordQueueAsked(int count) {
        if (count > 0) queueAsked.increment(count);
    }

    public void recordUserState(Session.UserState state) {
        if (state == null) return;
        Counter.builder("phase_d.state")
            .tag("state", state.name())
            .description("UserState 분포")
            .register(registry)
            .increment();
    }
}
