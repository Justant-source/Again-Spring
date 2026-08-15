package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the canonical line-break normalization used across the shortform pipeline
 * (2026-08-16 shortform text quality fix) — real CRLF, literal backslash-n/backslash-r,
 * Won-sign-n/Won-sign-r (Korean keyboard backslash), and legacy JSON backslash-u escapes
 * must all collapse to a real newline.
 */
class MarketingBriefTextTest {

    @Test
    void nullPassesThrough() {
        assertThat(MarketingBriefText.normalize(null)).isNull();
    }

    @Test
    void plainTextUnchanged() {
        assertThat(MarketingBriefText.normalize("평범한 한 줄 텍스트")).isEqualTo("평범한 한 줄 텍스트");
    }

    @Test
    void realNewlinePreserved() {
        assertThat(MarketingBriefText.normalize("첫 줄\n둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void crlfCollapsesToLf() {
        assertThat(MarketingBriefText.normalize("첫 줄\r\n둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void loneCrCollapsesToLf() {
        assertThat(MarketingBriefText.normalize("첫 줄\r둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void literalBackslashNBecomesRealNewline() {
        assertThat(MarketingBriefText.normalize("첫 줄\\n둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void literalBackslashRBackslashNBecomesSingleNewline() {
        assertThat(MarketingBriefText.normalize("첫 줄\\r\\n둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void wonSignNBecomesRealNewline() {
        // On a Korean keyboard layout the backslash key types the Won sign (₩) — some
        // models/tools reproduce the \n escape bug using ₩ instead of \.
        assertThat(MarketingBriefText.normalize("첫 줄₩n둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void wonSignRWonSignNBecomesSingleNewline() {
        assertThat(MarketingBriefText.normalize("첫 줄₩r₩n둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void unicodeEscapeDecodes() {
        assertThat(MarketingBriefText.normalize("첫 줄\\u000A둘째 줄")).isEqualTo("첫 줄\n둘째 줄");
    }

    @Test
    void mixedLiteralAndRealNewlinesAllNormalize() {
        assertThat(MarketingBriefText.normalize("A\\n B\r\n C₩n D"))
            .isEqualTo("A\n B\n C\n D");
    }
}
