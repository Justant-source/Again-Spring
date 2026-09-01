package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XOpsTelegramAlertsTest {

    @Test
    void posted_includesKindUrlTargetAndBody() {
        String msg = XOpsTelegramAlerts.posted(
            "Justant-Bot 선댓글",
            new AsmClient.XPublishResult(true, "abc", "https://x.com/i/web/status/abc", null),
            "root-1",
            "너무귀여움 ㅋㅋ");

        assertThat(msg).contains("Justant-Bot");
        assertThat(msg).contains("선댓글");
        assertThat(msg).contains("https://x.com/i/web/status/abc");
        assertThat(msg).contains("https://x.com/i/web/status/root-1");
        assertThat(msg).contains("너무귀여움 ㅋㅋ");
    }

    @Test
    void posted_fallsBackToTweetIdWhenUrlMissing() {
        String msg = XOpsTelegramAlerts.posted(
            "대댓글",
            new AsmClient.XPublishResult(true, "xyz", null, null),
            "parent",
            "공감돼요");

        assertThat(msg).contains("https://x.com/i/web/status/xyz");
        assertThat(msg).contains("공감돼요");
    }

    @Test
    void originalPosted_isNotificationNotDrill() {
        String msg = XOpsTelegramAlerts.originalPosted(
            new AsmClient.XPublishResult(true, "orig", "https://x.com/i/web/status/orig", null),
            "https://againspring.net/community/1001?utm_source=x",
            "그 마음 알겠음");

        assertThat(msg).contains("원글");
        assertThat(msg).contains("사연 스쿱");
        assertThat(msg).contains("https://x.com/i/web/status/orig");
        assertThat(msg).contains("againspring.net/community/1001");
        assertThat(msg).contains("그 마음 알겠음");
        assertThat(msg).doesNotContain("/drill");
    }
}
