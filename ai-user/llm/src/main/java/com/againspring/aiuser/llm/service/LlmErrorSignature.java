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
        // 2026-06-12 인시던트: "I can't help with this request" / 한국어 거절이 시그니처 미스로 게시됨
        "can't help with this",
        "cannot help with this",
        "unable to help with",
        "i can't assist",
        "cannot assist with",
        "role-play as",
        "this is asking me to",
        "이 요청을 도와드릴 수 없",
        "요청을 도와드릴 수가 없",
        "죄송하지만 저는 이 요청",
        "이 프롬프트는",
        "프롬프트 인젝션",
        "not set up to generate",
        "i need to be direct: i can't",
        "i need to be direct: i'm",
        "i need to clarify: i'm",
        "i need to be transparent",
        "i appreciate you",
        "i'm an ai",
        "i am an ai",
        "as an ai",
        "저는 ai",
        // 2026-06-18 언어-가드 보완: 시그니처 미스 방어용 보조 패턴
        "i can't fulfill",
        "i can't write this",
        "i can't write this comment",
        "i can't write this content",
        "i can't write this response",
        "i can't do this",
        "i appreciate the context",
        "i appreciate the detailed request",
        "i appreciate the detailed instructions",
        "these instructions ask me",
        "the instructions ask me",
        "actual operating online community",
        "operating online community",
        "authentic community member",
        "genuine community member",
        "real human user",
        "posing as a real user",
        "designed to appear authentic",
        "inauthentic engagement",
        "community participation",
        "이 요청은 도와드릴 수 없습니다",
        "이 요청은 수행할 수 없습니다",
        "실제 운영 중인",
        "실제 온라인 커뮤니티",
        "진정성 있는 사용자",
        "허위 정보 및 스푸핑",
        "조작된 커뮤니티 활동",
        "가짜 페르소나",
        "신원 위장",
        "사용자 조작",
        "진정성에 손상"
    );

    private static final double MIN_KOREAN_RATIO = 0.10;
    private static final int MIN_KOREAN_CHECK_LEN = 20;

    /** 한국어 AI 콘텐츠에 한글이 사실상 없으면(비율<10%) 영어 거절·오류로 판정. */
    private static boolean hasInsufficientKorean(String text) {
        long significant = text.chars().filter(c -> c > 32).count();
        if (significant < MIN_KOREAN_CHECK_LEN) return false;
        long korean = text.chars().filter(c ->
                (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0x1100 && c <= 0x11FF)
                || (c >= 0x3130 && c <= 0x318F)).count();
        return (double) korean / significant < MIN_KOREAN_RATIO;
    }

    /** 텍스트에 제공자 오류 시그니처가 포함되면 true. */
    public static boolean looksLikeProviderError(String text) {
        if (text == null || text.isBlank()) return false;
        if (hasInsufficientKorean(text)) return true;
        String t = text.toLowerCase();
        for (String s : SIGNATURES) {
            if (t.contains(s)) return true;
        }
        return false;
    }
}
