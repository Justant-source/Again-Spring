package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class XManualStatusClassifierTest {

    private static final String OURS = "againspring_net";

    @Test
    void replyToSomeoneElse_withHumanText_isManualReply() {
        var s = XManualStatusClassifier.Status.reply(
            "1", "@ceolmh3 꺼드럭은 더늘크크가 썼던 말인데 ㅋㅋㅋ", "ceolmh3");
        assertThat(XManualStatusClassifier.isManual(s, OURS)).isTrue();
        assertThat(XManualStatusClassifier.classify(s, OURS))
            .isEqualTo(XManualStatusClassifier.Classification.MANUAL_REPLY);
    }

    @Test
    void selfReplyEmptyOrUrl_isAutoThread() {
        assertThat(XManualStatusClassifier.isManual(
            XManualStatusClassifier.Status.reply("2", "", OURS), OURS)).isFalse();
        assertThat(XManualStatusClassifier.isManual(
            XManualStatusClassifier.Status.reply(
                "3", "https://againspring.net/community/post_abc?utm_source=x", OURS), OURS))
            .isFalse();
        assertThat(XManualStatusClassifier.classify(
            XManualStatusClassifier.Status.reply("2", "", OURS), OURS))
            .isEqualTo(XManualStatusClassifier.Classification.NOT_MANUAL);
    }

    @Test
    void brandHashtagHook_isNotManual() {
        var s = XManualStatusClassifier.Status.post(
            "4", "남친 폰 열자마자 소름 돋았다\n\n#다시봄 #againspring");
        assertThat(XManualStatusClassifier.isManual(s, OURS)).isFalse();
        assertThat(XManualStatusClassifier.classify(s, OURS))
            .isEqualTo(XManualStatusClassifier.Classification.NOT_MANUAL);
    }

    @Test
    void quoteWithCommentary_isManualReply() {
        var s = XManualStatusClassifier.Status.quote("5", "너무귀여움 ㅋㅋㅋㅋ", "원글");
        assertThat(XManualStatusClassifier.isManual(s, OURS)).isTrue();
        assertThat(XManualStatusClassifier.classify(s, OURS))
            .isEqualTo(XManualStatusClassifier.Classification.MANUAL_REPLY);
    }

    @Test
    void originalTweet_withoutReplyOrQuote_isManualPost() {
        var s = XManualStatusClassifier.Status.post("5b", "벌써자?");
        assertThat(XManualStatusClassifier.isManual(s, OURS)).isTrue();
        assertThat(XManualStatusClassifier.classify(s, OURS))
            .isEqualTo(XManualStatusClassifier.Classification.MANUAL_POST);
    }

    @Test
    void mentionOnlyOrBlank_isNotManual() {
        assertThat(XManualStatusClassifier.isManual(
            XManualStatusClassifier.Status.reply("6", "@ceolmh3", "ceolmh3"), OURS)).isFalse();
    }

    @Test
    void ledgerPostedId_isNotManual() {
        assertThat(XManualStatusClassifier.isManual(
            XManualStatusClassifier.Status.reply(
                "auto-1", "@ceolmh3 너무귀여움 ㅋㅋㅋㅋ", "ceolmh3"),
            OURS,
            Set.of("auto-1"))).isFalse();
        assertThat(XManualStatusClassifier.classify(
            XManualStatusClassifier.Status.reply(
                "auto-1", "@ceolmh3 너무귀여움 ㅋㅋㅋㅋ", "ceolmh3"),
            OURS,
            Set.of("auto-1")))
            .isEqualTo(XManualStatusClassifier.Classification.NOT_MANUAL);
        assertThat(XManualStatusClassifier.isManual(
            XManualStatusClassifier.Status.reply(
                "man-1", "@ceolmh3 힘빠지긴 할듯", "ceolmh3"),
            OURS,
            Set.of("auto-1"))).isTrue();
    }
}
