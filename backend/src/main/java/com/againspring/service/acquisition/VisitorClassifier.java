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

    /**
     * UA에 이 조각이 들어가면 봇으로 본다. 소문자 비교.
     *
     * <p>주의: "mobile safari"처럼 정상 브라우저에 흔한 문자열과 겹치지 않는 조각만 넣는다.
     * 오탐은 실제 방문자를 통계에서 지워버리므로 미탐보다 비싸다.
     */
    private static final List<String> BOT_TOKENS = List.of(
        "bot", "spider", "crawler", "crawl", "slurp",
        "googlebot", "bingbot", "yeti", "duckduckbot", "baiduspider", "yandex",
        "twitterbot", "facebookexternalhit", "linkedinbot", "telegrambot", "discordbot",
        "slackbot", "whatsapp", "kakaotalk-scrap", "embedly", "quora link preview",
        "curl/", "wget", "python-requests", "python-httpx", "go-http-client",
        "java/", "okhttp", "axios", "node-fetch", "libwww-perl", "httpclient",
        "headlesschrome", "phantomjs", "puppeteer", "playwright", "lighthouse",
        "google-safety", "paloaltonetworks", "censys", "masscan", "zgrab",
        "ahrefsbot", "semrushbot", "mj12bot", "dotbot", "petalbot", "trendictionbot",
        "applebot", "pinterestbot", "redditbot", "linkfluence", "expanse"
    );

    /** 데이터센터 사업자 대역에서 온 브라우저 UA는 사람으로 치지 않는다(t.co 검사 봇 사례). */
    private static final List<String> DATACENTER_ASN_HINTS = List.of(
        "ovh", "hetzner", "digitalocean", "linode", "vultr", "amazon", "google cloud"
    );

    public boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            // UA가 없는 요청은 브라우저가 아니다. 사람 통계에 넣지 않는다.
            return true;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String token : BOT_TOKENS) {
            if (ua.contains(token)) {
                return true;
            }
        }
        // 정상 브라우저는 예외 없이 Mozilla/로 시작한다.
        return !ua.startsWith("mozilla/");
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
