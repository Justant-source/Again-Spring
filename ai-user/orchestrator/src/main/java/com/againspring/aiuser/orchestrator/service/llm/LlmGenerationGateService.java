package com.againspring.aiuser.orchestrator.service.llm;

import com.againspring.aiuser.orchestrator.domain.LlmGenerationGate;
import com.againspring.aiuser.orchestrator.repository.LlmGenerationGateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * LlmGenerationGate Service: circuit breaker를 통한 LLM 생성 홀딩.
 *
 * <p>GENERATION(콘텐츠 생성)만 차단 — PUBLISHING(기존 콘텐츠 발행)은 계속됨.
 * 관리 엔드포인트로만 제어 (admin /admin/trigger/llm-generation-*)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmGenerationGateService {

    private final LlmGenerationGateRepository gateRepository;

    /**
     * 생성 게이트 상태 조회.
     * @return true = 생성 홀딩 상태, false = 생성 진행 가능
     */
    @Transactional(readOnly = true)
    public boolean isHeld() {
        return getGate().map(gate -> "HELD".equals(gate.getState())).orElse(false);
    }

    /**
     * 생성 게이트를 HELD 상태로 설정 (LLM 장애 시 호출).
     * @param reason 홀딩 사유 (nullable)
     */
    @Transactional
    public void hold(String reason) {
        LlmGenerationGate gate = getOrCreateGate();
        gate.setState("HELD");
        gate.setLastHeldAt(Instant.now());
        gate.setReason(reason);
        gate.setUpdatedAt(Instant.now());
        gateRepository.save(gate);
        log.info("[LlmGenerationGate] Generation HELD. reason={}", reason);
    }

    /**
     * 생성 게이트를 ACTIVE 상태로 설정 (정상화 후 호출).
     */
    @Transactional
    public void resume() {
        LlmGenerationGate gate = getOrCreateGate();
        gate.setState("ACTIVE");
        gate.setReason(null);
        gate.setUpdatedAt(Instant.now());
        gateRepository.save(gate);
        log.info("[LlmGenerationGate] Generation RESUMED");
    }

    /**
     * 현재 게이트 상태 조회 (디버그/모니터링용).
     * @return 현재 gate 상태, 또는 기본값
     */
    @Transactional(readOnly = true)
    public LlmGenerationGate getCurrentState() {
        return getOrCreateGate();
    }

    /**
     * 싱글톤 게이트 행 조회/생성 (id=1).
     */
    private LlmGenerationGate getOrCreateGate() {
        return gateRepository.findById(1).orElseGet(() -> {
            LlmGenerationGate gate = LlmGenerationGate.builder()
                    .id(1)
                    .state("ACTIVE")
                    .reason(null)
                    .updatedAt(Instant.now())
                    .build();
            return gateRepository.save(gate);
        });
    }

    /**
     * Optional 래퍼 (Optional이 필요한 경우용).
     */
    private java.util.Optional<LlmGenerationGate> getGate() {
        return gateRepository.findById(1);
    }
}
