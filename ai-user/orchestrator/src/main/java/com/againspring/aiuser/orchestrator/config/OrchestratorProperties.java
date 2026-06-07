package com.againspring.aiuser.orchestrator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-user")
public class OrchestratorProperties {
    private boolean enabled = false;
    private String tickCron = "0 */10 * * * *";
    private int dailyGlobalCap = 200;
    private String botPassword = "ai-user-dev-pw-2026";
    private String backendBaseUrl = "http://againspring-backend-dev:8080";
    private String llmAiUserUrl = "http://againspring-llm-ai-user:8092";
    private int personaTarget = 10;
    private String personasDir = "/app/personas";
    private boolean forceActive = false;  // 시간대 무관 강제 활성 (dev 테스트용)
    /** 보조 백엔드 URL (prod↔dev 동시 게시). 빈 문자열이면 미사용. */
    private String secondaryBackendBaseUrl = "";

    /** 콘텐츠 인식 좋아요·투표 결정 설정. */
    private ContentAwareDecisions contentAwareDecisions = new ContentAwareDecisions();

    public boolean isContentAwareEnabled() {
        return contentAwareDecisions.isEnabled();
    }

    public int getAnalysisBudgetPerTick() {
        return contentAwareDecisions.getAnalysisBudgetPerTick();
    }

    @Getter
    @Setter
    public static class ContentAwareDecisions {
        /** 콘텐츠 인식 결정 on/off. off면 기존 affinity/bias 동작. */
        private boolean enabled = true;
        /** 틱당 신규 글 분석 LLM 호출 상한 (토큰 통제). */
        private int analysisBudgetPerTick = 3;
    }
}
