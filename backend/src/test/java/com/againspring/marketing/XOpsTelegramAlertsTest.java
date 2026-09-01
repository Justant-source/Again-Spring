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
}
