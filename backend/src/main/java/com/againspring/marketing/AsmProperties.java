package com.againspring.marketing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for ASM (Again-Spring-Marketing) service
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "asm")
public class AsmProperties {

    private String baseUrl;
    private String apiToken;
    private long pollIntervalMs;
    private int requestTimeoutMs;
    private boolean enabled;
}
