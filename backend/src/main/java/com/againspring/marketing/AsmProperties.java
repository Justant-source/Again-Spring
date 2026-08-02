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
    private String callbackToken;
    private String callbackBaseUrl;
    private long xThreadPollIntervalMs = 600000; // 10 minutes default

    /**
     * Lower bound for 24h auto-publish eligibility ({@code post.createdAt >= since}).
     * Required when the publish trigger is enabled — empty/blank means skip all
     * auto-publish (fail-closed), so a backlog cannot flood live X/IG accounts.
     * ISO-8601 instant, e.g. {@code 2026-08-02T08:43:52Z}.
     */
    private String autoPublishSince;
}
