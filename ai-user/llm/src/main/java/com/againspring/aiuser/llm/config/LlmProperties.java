package com.againspring.aiuser.llm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the LLM worker module.
 * Maps YAML configuration under 'llm' prefix to Java fields.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    /** Structured generation failure alerting settings. */
    private StructuredGeneration structured = new StructuredGeneration();

    @Getter
    @Setter
    public static class StructuredGeneration {
        /** Enable/disable structured-generation PARSE_FAIL alerting via Telegram. */
        private boolean failureAlertsEnabled = true;
        /** Number of PARSE_FAIL events within window to trigger alert. */
        private int parseFailThreshold = 3;
        /** Time window in minutes for PARSE_FAIL counting. */
        private int parseFailWindowMinutes = 30;
        /** Cooldown in minutes after alert sent (suppresses duplicate alerts). */
        private int parseFailCooldownMinutes = 360;
    }
}
