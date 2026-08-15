package com.againspring.marketing;

import java.util.regex.Pattern;

/**
 * Canonical line-break normalization for every marketing brief text field (hook, title,
 * script, captions, comments) shared by AS brief building, and mirrored by ASM's
 * platform-overlay step and WaggleBot's render input (2026-08-16).
 *
 * <p>Handles four distinct sources of a "line break" that reach this pipeline:
 * <ul>
 *   <li>real CRLF / lone CR bytes (Windows-origin text, some LLM providers)</li>
 *   <li>literal two-character {@code \n} / {@code \r} (LLM wrote the escape instead of
 *       emitting an actual newline — legacy structured-output bug)</li>
 *   <li>{@code ₩n} / {@code ₩r} — on a Korean keyboard layout the backslash key types the
 *       Won sign, so some models/tools reproduce the same literal-escape bug using ₩</li>
 *   <li>JSON backslash-u Unicode escapes left over from a legacy JSON-escaped store</li>
 * </ul>
 * All four collapse to a single real {@code \n} (or, for CRLF, first to {@code \n} via a
 * dedicated CRLF pass so no stray {@code \r} survives).
 */
public final class MarketingBriefText {

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\+u[0-9a-fA-F]{4}");

    // A *run* of one-or-more backslashes (or Won signs) immediately before r/n collapses to a
    // single real newline — legacy JSON-escaped stores can double/triple-escape a backslash
    // adjacent to a newline escape (e.g. a literal backslash followed by "\n" serializes as
    // "\\\n"), and the whole run has always been treated as one escape, not a preserved
    // literal backslash + newline. Combo (r-then-n) patterns run first so paired CRLF-as-text
    // collapses to exactly one newline instead of two.
    private static final Pattern ESCAPED_CRLF_RUN = Pattern.compile("\\\\+r\\\\+n");
    private static final Pattern WON_CRLF_RUN = Pattern.compile("₩+r₩+n");
    private static final Pattern ESCAPED_LF_RUN = Pattern.compile("\\\\+n");
    private static final Pattern WON_LF_RUN = Pattern.compile("₩+n");
    private static final Pattern ESCAPED_CR_RUN = Pattern.compile("\\\\+r");
    private static final Pattern WON_CR_RUN = Pattern.compile("₩+r");

    private MarketingBriefText() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String s = value;
        // Real bytes first (actual control characters, no run-collapsing ambiguity).
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        // Literal escape sequences a model/legacy store may have emitted verbatim instead of
        // a real newline — combo run before the standalone runs (see field comment above).
        s = ESCAPED_CRLF_RUN.matcher(s).replaceAll("\n");
        s = WON_CRLF_RUN.matcher(s).replaceAll("\n");
        s = ESCAPED_LF_RUN.matcher(s).replaceAll("\n");
        s = WON_LF_RUN.matcher(s).replaceAll("\n");
        s = ESCAPED_CR_RUN.matcher(s).replaceAll("\n");
        s = WON_CR_RUN.matcher(s).replaceAll("\n");
        if (UNICODE_ESCAPE.matcher(s).find()) {
            s = decodeUnicodeEscapes(s);
        }
        return s;
    }

    private static String decodeUnicodeEscapes(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        int index = 0;
        while (index < value.length()) {
            char character = value.charAt(index);
            if (character == '\\' && index + 5 < value.length() && value.charAt(index + 1) == 'u'
                && isHex(value, index + 2, index + 6)) {
                normalized.append((char) Integer.parseInt(value.substring(index + 2, index + 6), 16));
                index += 6;
            } else {
                normalized.append(character);
                index++;
            }
        }
        return normalized.toString();
    }

    private static boolean isHex(String value, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            if (Character.digit(value.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
