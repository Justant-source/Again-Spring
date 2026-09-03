package com.againspring.aiuser.llm.service;

import java.util.Locale;

/**
 * 워커가 실행할 수 있는 LLM 백엔드. 요청 DTO의 {@code provider} 필드 하나로 고른다.
 * CLAUDE = Claude Code CLI(OAuth) · CODEX = Codex CLI · API = Anthropic 호환 HTTP(키) · STUB = 픽스처 재생(LLM 미호출).
 */
public enum LlmProvider {
    CLAUDE, CODEX, API, STUB;

    public static LlmProvider parse(String value) {
        if (value == null || value.isBlank()) return CLAUDE;
        String v = value.trim().toUpperCase(Locale.ROOT);
        if ("CLI".equals(v)) return CLAUDE; // 구 backend 필드 값 호환
        try {
            return valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported LLM provider: " + value);
        }
    }

    /** 구 DTO 호환: provider가 있으면 그것, 없으면 backend(CLI|API), 둘 다 없으면 CLAUDE. */
    public static LlmProvider parseLegacy(String provider, String backend) {
        if (provider != null && !provider.isBlank()) return parse(provider);
        return parse(backend);
    }
}
