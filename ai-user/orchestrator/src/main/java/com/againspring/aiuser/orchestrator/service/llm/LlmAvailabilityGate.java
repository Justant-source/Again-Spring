package com.againspring.aiuser.orchestrator.service.llm;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.domain.LlmGenerationGate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * 워커 provider 상태로 llm_generation_gate를 자동 hold/resume한다.
 * 사람이 건 hold(reason이 "auto:"로 시작하지 않음)는 건드리지 않는다.
 * 이유: 2026-09-03 감사 — 문서는 "watchdog이 자동 복구"라 했지만 hold()를 부르는 코드가 admin 수동 호출뿐이었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAvailabilityGate {
    static final String AUTO_PREFIX = "auto:llm-auth-down";

    private final LlmAiUserClient llmClient;
    private final LlmGenerationGateService gateService;

    @Scheduled(cron = "${ai-user.llm-availability-cron:0 */5 * * * *}")
    public void check() {
        Optional<Map<String, Object>> status = llmClient.providersStatus();
        if (status.isEmpty()) return;
        String downReason = downReason(status.get(), "claude");
        LlmGenerationGate gate = gateService.getCurrentState();
        boolean held = "HELD".equals(gate.getState());
        boolean autoHeld = held && gate.getReason() != null && gate.getReason().startsWith(AUTO_PREFIX);
        if (downReason != null) {
            if (!held) {
                gateService.hold(AUTO_PREFIX + ": " + downReason);
                log.warn("[LlmAvailabilityGate] auto-hold: {}", downReason);
            }
            return;
        }
        if (autoHeld) {
            gateService.resume();
            log.info("[LlmAvailabilityGate] auto-resume: provider back UP");
        }
    }

    @SuppressWarnings("unchecked")
    private static String downReason(Map<String, Object> status, String provider) {
        Object row = status.get(provider);
        if (!(row instanceof Map<?, ?> m)) return null;
        if (!"AUTH_DOWN".equals(m.get("state"))) return null;
        Object r = m.get("reason");
        return r == null ? provider + " AUTH_DOWN" : provider + ": " + r;
    }
}
