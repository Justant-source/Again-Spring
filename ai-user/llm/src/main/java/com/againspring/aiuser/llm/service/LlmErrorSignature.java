package com.againspring.aiuser.llm.service;

import java.util.List;

/**
 * 제공자(Claude API/CLI) 오류 텍스트가 생성 콘텐츠로 새어 prod에 게시되는 것을 차단.
 *
 * 배경(2026-06-07 인시던트): API 크레딧/Max 쿼터 소진 시 "Credit balance is too low" 같은
 * 영어 오류 문자열이 그대로 글·댓글 본문으로 게시됨. 토큰 부족(CLI/API)은 콘텐츠가 아니라
 * 오류로 처리해야 한다. → 인보커가 이 시그니처를 감지하면 예외를 던져 생성 실패시킨다.
 *
 * 시그니처는 모두 영어 — 정상 AI 콘텐츠(한국어)와 충돌하지 않는다.
 */
public final class LlmErrorSignature {

    private LlmErrorSignature() {}

    private static final List<String> SIGNATURES = List.of(
        // 토큰/크레딧 오류
        "credit balance",
        "too low to access",
        "purchase credits",
        "plans & billing",
        "usage limit",
        "reached your usage",
        "5-hour limit",
        "rate limit",
        "rate_limit",
        "overloaded",
        "invalid_request_error",
        "authentication_error",
        "permission_error",
        "api_error",
        "anthropic api",
        "insufficient credit",
        "too many requests",
        "service unavailable",
        "internal server error",
        // LLM 자기 정체 노출 / 역할극 거절 (프록시 라우팅 오류 등)
        "i'm kiro",
        "i am kiro",
        "저는 kiro",
        "kiro입니다",
        "i'm claude",
        "i am claude",
        "i'm an ai assistant",
        "저는 claude",
        "i can't discuss that",
        "i cannot roleplay",
        "i'm not able to roleplay",
        "not able to roleplay",
        "can't roleplay",
        "cannot roleplay as",
        "won't roleplay",
        "not set up to generate",
        "i need to be direct: i can't",
        "i need to be direct: i'm",
        "i need to clarify: i'm",
        "i need to be transparent",
        "i appreciate you",
        "i'm an ai",
        "i am an ai",
        "as an ai",
        "저는 ai"
    );

    /** 텍스트에 제공자 오류 시그니처가 포함되면 true. */
    public static boolean looksLikeProviderError(String text) {
        if (text == null || text.isBlank()) return false;
        String t = text.toLowerCase();
        for (String s : SIGNATURES) {
            if (t.contains(s)) return true;
        }
        return false;
    }
}
