package com.againspring.service.acquisition;

/**
 * 방문자 분류값. visit_events.visitor_class에 name() 문자열로 저장한다(DB ENUM 아님).
 *
 * <p>is_bot은 이 값의 파생이다: HUMAN_* 와 UNKNOWN_LEGACY는 false(기존 동작 보존),
 * 나머지는 true.
 */
public enum VisitorClass {
    HUMAN_KR(false),
    HUMAN_OVERSEAS(false),
    HUMAN_UNKNOWN_GEO(false),
    /** 우리 ASM 스크린샷 캡처. */
    BOT_SELF(true),
    /** 보안/링크 안전검사. */
    BOT_SCANNER(true),
    /** 검색·SEO 크롤러. */
    BOT_CRAWLER(true),
    /** 링크 미리보기 수집기. */
    BOT_SOCIAL_PREVIEW(true),
    /** headless/curl/라이브러리/UA 없음. */
    BOT_AUTOMATION(true),
    /** 브라우저 UA지만 직전 스캐너 방문 직후 해외에서 들어온 링크검사 프록시 의심. */
    SUSPECT_SCANNER_PROXY(true),
    /** UA 수집(2026-08-29) 이전의 user_agent NULL 행. */
    UNKNOWN_LEGACY(false);

    private final boolean bot;

    VisitorClass(boolean bot) {
        this.bot = bot;
    }

    public boolean bot() {
        return bot;
    }
}
