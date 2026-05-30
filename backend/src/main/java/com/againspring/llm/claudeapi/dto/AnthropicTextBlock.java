package com.againspring.llm.claudeapi.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Anthropic Messages API text block with optional cache_control.
 * Supports ephemeral prompt caching with configurable TTL.
 *
 * TTL 선택 기준:
 *   cached()     → 5분 (SESSION_STATIC, HISTORY — 세션별 변동)
 *   cachedLong() → 1시간 (GLOBAL_STATIC — 배포 단위로만 변경)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public record AnthropicTextBlock(
    String type,           // always "text"
    String text,
    @JsonProperty("cache_control") CacheControl cacheControl
) {
    /**
     * Cache control with optional TTL.
     * ttl: null = 5m (Anthropic default), "1h" = 1시간
     * Anthropic 허용값: "5m" | "1h"
     */
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CacheControl(String type, String ttl) {
        /** 5분 TTL (기본) */
        public static CacheControl ephemeral5m() {
            return new CacheControl("ephemeral", null);
        }
        /** 1시간 TTL (전역 고정 프리픽스용) */
        public static CacheControl ephemeral1h() {
            return new CacheControl("ephemeral", "1h");
        }
    }

    /** 캐시 없는 텍스트 블록 */
    public static AnthropicTextBlock text(String text) {
        return AnthropicTextBlock.builder()
            .type("text").text(text).cacheControl(null).build();
    }

    /** 5분 TTL 캐시 블록 — SESSION_STATIC, HISTORY (세션 내 고정이나 세션 간 변동) */
    public static AnthropicTextBlock cached(String text) {
        return AnthropicTextBlock.builder()
            .type("text").text(text)
            .cacheControl(CacheControl.ephemeral5m()).build();
    }

    /** 1시간 TTL 캐시 블록 — GLOBAL_STATIC (system.md, gottman, nvc, chat_mode) */
    public static AnthropicTextBlock cachedLong(String text) {
        return AnthropicTextBlock.builder()
            .type("text").text(text)
            .cacheControl(CacheControl.ephemeral1h()).build();
    }
}
