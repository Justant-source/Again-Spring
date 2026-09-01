package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XManualStatusClassifierTest {

    private static final String OURS = "againspring_net";

    @Test
    void replyToSomeoneElse_withHumanText_isManual() {
        assertThat(XManualStatusClassifier.isManual(new XManualStatusClassifier.Status(
            "1", "@ceolmh3 꺼드럭은 더늘크크가 썼던 말인데 ㅋㅋㅋ", "ceolmh3", false), OURS))
            .isTrue();
    }

    @Test
    void selfReplyEmptyOrUrl_isAutoThread() {
        assertThat(XManualStatusClassifier.isManual(new XManualStatusClassifier.Status(
            "2", "", OURS, false), OURS)).isFalse();
        assertThat(XManualStatusClassifier.isManual(new XManualStatusClassifier.Status(
            "3", "https://againspring.net/community/post_abc?utm_source=x", OURS, false), OURS))
            .isFalse();
    }

    @Test
    void brandHashtagHook_isAuto() {
        assertThat(XManualStatusClassifier.isManual(new XManualStatusClassifier.Status(
            "4", "남친 폰 열자마자 소름 돋았다\n\n#다시봄 #againspring", null, false), OURS))
            .isFalse();
    }

    @Test
    void quoteWithCommentary_isManual() {
        assertThat(XManualStatusClassifier.isManual(new XManualStatusClassifier.Status(
            "5", "너무귀여움 ㅋㅋㅋㅋ", null, true), OURS)).isTrue();
    }

    @Test
    void originalTweet_withoutReplyOrQuote_isNotManual() {
        assertThat(XManualStatusClassifier.isManual(new XManualStatusClassifier.Status(
            "5b", "벌써자?", null, false), OURS)).isFalse();
    }

    @Test
    void mentionOnlyOrBlank_isNotManual() {
        assertThat(XManualStatusClassifier.isManual(new XManualStatusClassifier.Status(
            "6", "@ceolmh3", "ceolmh3", false), OURS)).isFalse();
    }

    @Test
    void ledgerPostedId_isNotManual() {
        assertThat(XManualStatusClassifier.isManual(
            new XManualStatusClassifier.Status(
                "auto-1", "@ceolmh3 너무귀여움 ㅋㅋㅋㅋ", "ceolmh3", false),
            OURS,
            java.util.Set.of("auto-1"))).isFalse();
        assertThat(XManualStatusClassifier.isManual(
            new XManualStatusClassifier.Status(
                "man-1", "@ceolmh3 힘빠지긴 할듯", "ceolmh3", false),
            OURS,
            java.util.Set.of("auto-1"))).isTrue();
    }
}
