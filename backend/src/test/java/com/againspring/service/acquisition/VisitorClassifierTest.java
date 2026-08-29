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
}
