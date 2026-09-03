package com.againspring.aiuser.llm.service;

/** 하위호환 파사드 — 판정 데이터는 {@link LlmErrorSignatures}(JSON SSOT). */
public final class LlmErrorSignature {
    private LlmErrorSignature() {}

    /** JSON 분석 응답(post_analysis 등)은 한글 비율 검사 면제. */
    private static boolean looksLikeJsonResponse(String text) {
        String t = text.trim();
        if (t.startsWith("```json")) t = t.substring(7).stripLeading();
        if (t.startsWith("```")) t = t.substring(3).stripLeading();
        return t.startsWith("{") && t.contains("\"author_sympathy\"");
    }

    /** 텍스트에 제공자 오류 시그니처가 포함되면 true. */
    public static boolean looksLikeProviderError(String text) {
        if (text == null || text.isBlank()) return false;
        if (looksLikeJsonResponse(text)) return false;
        LlmErrorSignatures s = LlmErrorSignatures.get();
        if (s.hasInsufficientKorean(text)) return true;
        return s.containsSignature(text.toLowerCase(java.util.Locale.ROOT));
    }
}
