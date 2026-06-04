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
    private String llmAiUserUrl = "http://againspring-llm-ai-user-dev:8092";
    private int personaTarget = 50;
    private String personasDir = "/app/personas";
    private boolean forceActive = false;  // 시간대 무관 강제 활성 (dev 테스트용)
}
