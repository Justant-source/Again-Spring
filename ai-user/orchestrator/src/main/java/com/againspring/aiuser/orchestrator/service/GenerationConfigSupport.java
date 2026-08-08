package com.againspring.aiuser.orchestrator.service;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * /admin/ai-user 저장값이 즉시 반영되도록 DB를 매 호출 읽는다 (캐시 없음).
 * 구조화 LLM 타임아웃·후보 풀 등 생성 경로 공통 설정.
 */
@Component
@RequiredArgsConstructor
public class GenerationConfigSupport {
    private final AiUserGenerationConfigRepository configRepository;
    private final OrchestratorProperties properties;

    public AiUserGenerationConfig current() {
        return configRepository.findById(1).orElse(null);
    }

    /** solo / paired / human-reply 구조화 호출에 넣을 timeoutMs. */
    public long bundleTimeoutMs() {
        long fallback = properties.getThreadPlan().getBundleTimeoutMs();
        AiUserGenerationConfig cfg = current();
        return cfg == null ? (fallback > 0 ? fallback : 600_000L) : cfg.resolveBundleTimeoutMs(fallback);
    }
}
