package com.againspring.service.acquisition;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 방문 요청을 봇/사람, 기기, 국가로 분류한다.
 *
 * <p>배경(2026-08-29): 유입 조사에서 "X에서 114건 유입"으로 보이던 것이 전부 OVH
 * 데이터센터 IP의 링크 검사 봇이었다. 봇을 걸러내지 않으면 0을 성과로 오독한다.
 * 판정은 저장 시점에 한 번 하고 근거(user_agent)를 함께 남겨, 규칙이 바뀌면 과거 행을
 * 다시 분류할 수 있게 한다.
 */
@Component
public class VisitorClassifier {

    /** 우리 ASM 캡처 표식(2026-10-18~). 소문자. */
    private static final String SELF_MARKER = "againspringcapture";

    /** 보안/링크 안전검사. */
    private static final List<String> SCANNER_TOKENS = List.of(
        "google-safety", "paloaltonetworks", "palo alto networks", "censys", "masscan", "zgrab", "expanse"
    );

    /** 검색·SEO 크롤러(구체 이름). 범용 "bot" 조각은 소셜 미리보기 뒤에서 검사한다. */
    private static final List<String> CRAWLER_TOKENS = List.of(
        "googlebot", "bingbot", "yeti", "duckduckbot", "baiduspider", "yandex",
        "ahrefsbot", "semrushbot", "mj12bot", "dotbot", "petalbot", "trendictionbot",
        "applebot", "linkfluence"
    );

    /** 링크 미리보기 수집기. */
    private static final List<String> SOCIAL_PREVIEW_TOKENS = List.of(
        "twitterbot", "facebookexternalhit", "linkedinbot", "telegrambot", "discordbot",
        "slackbot", "whatsapp", "kakaotalk-scrap", "embedly", "quora link preview",
        "pinterestbot", "redditbot"
    );

    /** 범용 크롤러 조각. "twitterbot"도 "bot"을 포함하므로 구체 분류 뒤에 검사한다. */
    private static final List<String> GENERIC_CRAWLER_TOKENS = List.of(
        "bot", "spider", "crawler", "crawl", "slurp"
    );

    /**
     * 자동화 도구/라이브러리/헤드리스.
     *
     * <p>주의: "mobile safari"처럼 정상 브라우저에 흔한 문자열과 겹치지 않는 조각만 넣는다.
     * 오탐은 실제 방문자를 통계에서 지워버리므로 미탐보다 비싸다.
     */
    private static final List<String> AUTOMATION_TOKENS = List.of(
        "curl/", "wget", "python-requests", "python-httpx", "go-http-client",
        "java/", "okhttp", "axios", "node-fetch", "libwww-perl", "httpclient",
        "headlesschrome", "phantomjs", "puppeteer", "playwright", "lighthouse"
    );

    /** 스캐너 직후 판정 창(초). 마이그레이션 V130 백필도 같은 값을 쓴다. */
    public static final int SCANNER_PROXIMITY_SECONDS = 10;

    /** 분류 결과. reason은 사람이 읽는 근거(80자 이내). */
    public record Classification(VisitorClass visitorClass, String reason) {
        public boolean bot() {
            return visitorClass.bot();
        }
    }

    /**
     * 표식 도입(2026-10-18) 이전 ASM 캡처가 보낸 Playwright 기본 UA 전체 문자열.
     * 부분 일치가 아니라 전체 일치만 봇으로 본다 — 실제 Linux 데스크톱 사용자를 지우지 않기 위해서다.
     * 최근 30일 "사람" 방문 1,117건 중 936건(84%)이 이 문자열이었다.
     */
    private static final String LEGACY_ASM_CAPTURE_UA =
        "mozilla/5.0 (x11; linux x86_64) applewebkit/537.36 (khtml, like gecko) chrome/125.0.0.0 safari/537.36";

    /** 데이터센터 사업자 대역에서 온 브라우저 UA는 사람으로 치지 않는다(t.co 검사 봇 사례). */
    private static final List<String> DATACENTER_ASN_HINTS = List.of(
        "ovh", "hetzner", "digitalocean", "linode", "vultr", "amazon", "google cloud"
    );

    /** 기존 호환: 국가를 모르는 상태의 봇 여부. */
    public boolean isBot(String userAgent) {
        return classify(userAgent, null).bot();
    }

    /**
     * UA와 국가로 분류한다(스캐너 근접 판정 제외 — {@link #applyScannerProximity} 참고).
     * 우선순위: SELF > SCANNER > CRAWLER > SOCIAL_PREVIEW > (범용 crawler) > AUTOMATION > HUMAN_*.
     */
    public Classification classify(String userAgent, String countryCode) {
        if (userAgent == null || userAgent.isBlank()) {
            // 새로 들어온 요청에 UA가 없으면 브라우저가 아니다. (과거 NULL 행은 백필이 UNKNOWN_LEGACY로 둔다)
            return new Classification(VisitorClass.BOT_AUTOMATION, "ua:missing");
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains(SELF_MARKER)) {
            return new Classification(VisitorClass.BOT_SELF, "ua:" + SELF_MARKER);
        }
        if (ua.equals(LEGACY_ASM_CAPTURE_UA)) {
            return new Classification(VisitorClass.BOT_SELF, "ua:legacy-playwright-default");
        }
        String hit = firstHit(ua, SCANNER_TOKENS);
        if (hit != null) {
            return new Classification(VisitorClass.BOT_SCANNER, "ua:" + hit);
        }
        hit = firstHit(ua, CRAWLER_TOKENS);
        if (hit != null) {
            return new Classification(VisitorClass.BOT_CRAWLER, "ua:" + hit);
        }
        hit = firstHit(ua, SOCIAL_PREVIEW_TOKENS);
        if (hit != null) {
            return new Classification(VisitorClass.BOT_SOCIAL_PREVIEW, "ua:" + hit);
        }
        hit = firstHit(ua, GENERIC_CRAWLER_TOKENS);
        if (hit != null) {
            return new Classification(VisitorClass.BOT_CRAWLER, "ua:" + hit);
        }
        hit = firstHit(ua, AUTOMATION_TOKENS);
        if (hit != null) {
            return new Classification(VisitorClass.BOT_AUTOMATION, "ua:" + hit);
        }
        // 정상 브라우저는 예외 없이 Mozilla/로 시작한다.
        if (!ua.startsWith("mozilla/")) {
            return new Classification(VisitorClass.BOT_AUTOMATION, "ua:non-browser");
        }
        if (countryCode == null || countryCode.isBlank()) {
            return new Classification(VisitorClass.HUMAN_UNKNOWN_GEO, "geo:none");
        }
        String cc = countryCode.toUpperCase(Locale.ROOT);
        return "KR".equals(cc)
            ? new Classification(VisitorClass.HUMAN_KR, "geo:KR")
            : new Classification(VisitorClass.HUMAN_OVERSEAS, "geo:" + cc);
    }

    /** 스캐너 근접 조회가 필요한 후보인가 — 해외 사람 분류이고 referrer가 없을 때만. */
    public boolean needsScannerProximityCheck(Classification base, String referrer) {
        return base.visitorClass() == VisitorClass.HUMAN_OVERSEAS
            && (referrer == null || referrer.isBlank());
    }

    /**
     * 링크검사 프록시 의심 판정(보수적).
     *
     * <p>규칙: 브라우저 UA로 해외({@code HUMAN_OVERSEAS})에서 왔고, referrer가 없으며,
     * 같은 utm_campaign(utm이 없으면 같은 path)의 BOT_SCANNER 행이 직전
     * {@value #SCANNER_PROXIMITY_SECONDS}초 안에 있었을 때만 SUSPECT로 바꾼다.
     * 한국 방문자와 국가 불명은 절대 바꾸지 않는다 — 오탐은 실제 방문자를 지운다.
     */
    public Classification applyScannerProximity(Classification base, boolean scannerNearby, boolean byCampaign) {
        if (!scannerNearby || base.visitorClass() != VisitorClass.HUMAN_OVERSEAS) {
            return base;
        }
        return new Classification(VisitorClass.SUSPECT_SCANNER_PROXY,
            "scanner<=" + SCANNER_PROXIMITY_SECONDS + "s:" + (byCampaign ? "campaign" : "path")
                + ",no-referrer,overseas");
    }

    private static String firstHit(String ua, List<String> tokens) {
        for (String token : tokens) {
            if (ua.contains(token)) {
                return token;
            }
        }
        return null;
    }

    public String deviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad") || (ua.contains("android") && !ua.contains("mobile"))) {
            return "tablet";
        }
        if (ua.contains("iphone") || ua.contains("android") || ua.contains("mobile")) {
            return "mobile";
        }
        return "desktop";
    }

    /** Cloudflare가 붙여주는 국가 코드. 앞단이 바뀌면 null이 되며 집계는 그대로 동작한다. */
    public String country(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String cc = request.getHeader("CF-IPCountry");
        if (cc == null || cc.isBlank() || "XX".equalsIgnoreCase(cc)) {
            return null;
        }
        return cc.length() > 8 ? cc.substring(0, 8) : cc.toUpperCase(Locale.ROOT);
    }

    public boolean looksLikeDatacenter(String asnOrOrg) {
        if (asnOrOrg == null) {
            return false;
        }
        String s = asnOrOrg.toLowerCase(Locale.ROOT);
        return DATACENTER_ASN_HINTS.stream().anyMatch(s::contains);
    }
}
