package com.againspring.aiuser.orchestrator.util;

/**
 * LLM/structured JSON이 실제 개행(0x0A) 대신 문자 그대로 "\n"을 넣는 사례 방어.
 * llm 모듈 {@code OutputSanitizer.normalizeLiteralNewlines}와 동일 규칙
 * (orchestrator는 llm 모듈을 compile 의존하지 않으므로 여기 복제).
 */
public final class LiteralNewlineNormalizer {
    private LiteralNewlineNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        return raw.replace("\\r\\n", "\n").replace("\\n", "\n");
    }
}
