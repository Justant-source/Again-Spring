package com.againspring.service.acquisition;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorClassifierTest {

    private final VisitorClassifier classifier = new VisitorClassifier();

    @Test
    @DisplayName("실제 유입 조사에서 사람으로 오인됐던 크롤러들을 봇으로 판정한다")
    void detectsCrawlersSeenInProduction() {
        // 2026-08-29 조사에서 nginx 로그에 실제로 찍혔던 UA들
        assertThat(classifier.isBot("Twitterbot/1.0")).isTrue();
        assertThat(classifier.isBot("Google-Safety")).isTrue();
        assertThat(classifier.isBot("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")).isTrue();
        assertThat(classifier.isBot("Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)")).isTrue();
        assertThat(classifier.isBot("TelegramBot (like TwitterBot)")).isTrue();
        assertThat(classifier.isBot("facebookexternalhit/1.1")).isTrue();
        assertThat(classifier.isBot("curl/8.5.0")).isTrue();
        assertThat(classifier.isBot("Hello from Palo Alto Networks, find out more about our scans")).isTrue();
        assertThat(classifier.isBot("Mozilla/5.0 (Windows NT 10.0; Win64; x64; trendictionbot0.5.0)")).isTrue();
    }

    @Test
    @DisplayName("ASM 캡처 트래픽은 봇이다 — 표식 UA와 과거 Playwright 기본 UA 전체 일치")
    void treatsAsmCaptureAsBot() {
        assertThat(classifier.isBot("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")).isTrue();
        assertThat(classifier.isBot("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 AgainSpringCapture")).isTrue();
        // 다른 버전의 Linux 데스크톱 사용자는 사람으로 남는다
        assertThat(classifier.isBot("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")).isFalse();
    }

    @Test
    @DisplayName("UA가 없거나 Mozilla로 시작하지 않으면 사람으로 세지 않는다")
    void treatsNonBrowserAsBot() {
        assertThat(classifier.isBot(null)).isTrue();
        assertThat(classifier.isBot("")).isTrue();
        assertThat(classifier.isBot("Go-http-client/2.0")).isTrue();
        assertThat(classifier.isBot("python-requests/2.31.0")).isTrue();
    }

    @Test
    @DisplayName("일반 브라우저는 사람으로 판정한다 — 오탐은 미탐보다 비싸다")
    void keepsRealBrowsers() {
        assertThat(classifier.isBot(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/26.6 Mobile/15E148 Safari/604.1")).isFalse();
        assertThat(classifier.isBot(
            "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/140.0.0.0 Mobile Safari/537.36")).isFalse();
        assertThat(classifier.isBot(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:148.0) Gecko/20100101 Firefox/148.0")).isFalse();
    }

    @Test
    @DisplayName("기기 분류 — 아이패드와 태블릿 안드로이드는 tablet")
    void classifiesDevice() {
        assertThat(classifier.deviceType("Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X)")).isEqualTo("mobile");
        assertThat(classifier.deviceType("Mozilla/5.0 (Linux; Android 15; K) Mobile Safari")).isEqualTo("mobile");
        assertThat(classifier.deviceType("Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)")).isEqualTo("tablet");
        assertThat(classifier.deviceType("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).isEqualTo("desktop");
        assertThat(classifier.deviceType(null)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("국가는 Cloudflare 헤더에서 읽고, 없거나 XX면 null")
    void readsCountryHeader() {
        MockHttpServletRequest kr = new MockHttpServletRequest();
        kr.addHeader("CF-IPCountry", "KR");
        assertThat(classifier.country(kr)).isEqualTo("KR");

        MockHttpServletRequest unknown = new MockHttpServletRequest();
        unknown.addHeader("CF-IPCountry", "XX");
        assertThat(classifier.country(unknown)).isNull();

        assertThat(classifier.country(new MockHttpServletRequest())).isNull();
        assertThat(classifier.country((HttpServletRequest) null)).isNull();
    }

    private static final String CHROME_WIN =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    private static final String MOBILE_KR =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.6 Mobile/15E148 Safari/604.1";

    private VisitorClass cls(String ua, String cc) {
        return classifier.classify(ua, cc).visitorClass();
    }

    @Test
    @DisplayName("각 클래스의 대표 UA가 올바른 분류와 is_bot 파생값을 갖는다")
    void classifiesRepresentativeUserAgents() {
        assertThat(cls("Google-Safety", "US")).isEqualTo(VisitorClass.BOT_SCANNER);
        assertThat(cls("Hello from Palo Alto Networks, find out more", "US")).isEqualTo(VisitorClass.BOT_SCANNER);
        assertThat(cls("Twitterbot/1.0", "US")).isEqualTo(VisitorClass.BOT_SOCIAL_PREVIEW);
        assertThat(cls("TelegramBot (like TwitterBot)", null)).isEqualTo(VisitorClass.BOT_SOCIAL_PREVIEW);
        assertThat(cls("facebookexternalhit/1.1", null)).isEqualTo(VisitorClass.BOT_SOCIAL_PREVIEW);
        assertThat(cls("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)", "US"))
            .isEqualTo(VisitorClass.BOT_CRAWLER);
        assertThat(cls("Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)", "KR")).isEqualTo(VisitorClass.BOT_CRAWLER);
        assertThat(cls("Mozilla/5.0 (compatible; SomeNewBot/1.0)", null)).isEqualTo(VisitorClass.BOT_CRAWLER);
        assertThat(cls("curl/8.5.0", null)).isEqualTo(VisitorClass.BOT_AUTOMATION);
        assertThat(cls("Go-http-client/2.0", null)).isEqualTo(VisitorClass.BOT_AUTOMATION);
        assertThat(cls("HeadlessChrome/120 Mozilla/5.0", null)).isEqualTo(VisitorClass.BOT_AUTOMATION);
        assertThat(cls(null, "KR")).isEqualTo(VisitorClass.BOT_AUTOMATION);
        assertThat(cls(MOBILE_KR, "KR")).isEqualTo(VisitorClass.HUMAN_KR);
        assertThat(cls(CHROME_WIN, "US")).isEqualTo(VisitorClass.HUMAN_OVERSEAS);
        assertThat(cls(CHROME_WIN, null)).isEqualTo(VisitorClass.HUMAN_UNKNOWN_GEO);
        assertThat(cls(CHROME_WIN, "kr")).isEqualTo(VisitorClass.HUMAN_KR);

        assertThat(classifier.classify(MOBILE_KR, "KR").bot()).isFalse();
        assertThat(classifier.classify("Google-Safety", "US").bot()).isTrue();
        assertThat(VisitorClass.UNKNOWN_LEGACY.bot()).isFalse();
        assertThat(VisitorClass.SUSPECT_SCANNER_PROXY.bot()).isTrue();
    }

    @Test
    @DisplayName("ASM 캡처는 BOT_SELF — 표식 UA와 과거 Playwright 기본 UA 전체 일치")
    void classifiesSelfCapture() {
        assertThat(cls("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36", "KR"))
            .isEqualTo(VisitorClass.BOT_SELF);
        assertThat(cls(CHROME_WIN + " AgainSpringCapture", "KR")).isEqualTo(VisitorClass.BOT_SELF);
        assertThat(cls("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36", "KR"))
            .isEqualTo(VisitorClass.HUMAN_KR);
    }

    @Test
    @DisplayName("우선순위 — SELF > SCANNER > CRAWLER > SOCIAL_PREVIEW > AUTOMATION")
    void priorityOrder() {
        assertThat(cls("curl/8 AgainSpringCapture Google-Safety Googlebot", null)).isEqualTo(VisitorClass.BOT_SELF);
        assertThat(cls("Google-Safety Googlebot Twitterbot curl/8", null)).isEqualTo(VisitorClass.BOT_SCANNER);
        assertThat(cls("Googlebot Twitterbot curl/8", null)).isEqualTo(VisitorClass.BOT_CRAWLER);
        assertThat(cls("Twitterbot curl/8", null)).isEqualTo(VisitorClass.BOT_SOCIAL_PREVIEW);
        assertThat(cls("Mozilla/5.0 curl/8", "KR")).isEqualTo(VisitorClass.BOT_AUTOMATION);
    }

    @Test
    @DisplayName("스캐너 근접 의심은 해외 + referrer 없음 + 스캐너 직후일 때만 SUSPECT, reason에 근거를 남긴다")
    void scannerProximity() {
        VisitorClassifier.Classification overseas = classifier.classify(CHROME_WIN, "US");
        VisitorClassifier.Classification kr = classifier.classify(CHROME_WIN, "KR");
        VisitorClassifier.Classification unknown = classifier.classify(CHROME_WIN, null);

        assertThat(classifier.needsScannerProximityCheck(overseas, null)).isTrue();
        assertThat(classifier.needsScannerProximityCheck(overseas, "")).isTrue();
        assertThat(classifier.needsScannerProximityCheck(overseas, "https://t.co/x")).isFalse();
        assertThat(classifier.needsScannerProximityCheck(kr, null)).isFalse();
        assertThat(classifier.needsScannerProximityCheck(unknown, null)).isFalse();

        VisitorClassifier.Classification suspect = classifier.applyScannerProximity(overseas, true, true);
        assertThat(suspect.visitorClass()).isEqualTo(VisitorClass.SUSPECT_SCANNER_PROXY);
        assertThat(suspect.bot()).isTrue();
        assertThat(suspect.reason()).contains("campaign").contains("no-referrer").contains("10s");
        assertThat(classifier.applyScannerProximity(overseas, false, true)).isEqualTo(overseas);
        assertThat(classifier.applyScannerProximity(kr, true, true)).isEqualTo(kr);
        assertThat(classifier.applyScannerProximity(overseas, true, false).reason()).contains("path");
    }
}
