package com.againspring.marketing;

import java.util.ArrayList;
import java.util.List;

/**
 * Candidate CSS Y values for X-thread story part crops on {@code /community/{id}/read}.
 * Authoritative cuts are still ASM DOM measurements; these are verification + fallback.
 *
 * <p>Layout constants mirror {@code frontend/app/community/[id]/read/page.tsx} at viewport 430.
 */
public final class CaptureHeightCalculator {

    public static final int VIEWPORT_WIDTH = 430;
    public static final int PAGE_PAD_X = 24;
    public static final int PAGE_PAD_TOP = 16;
    public static final int CARD_PAD_X = 22;
    public static final int CARD_PAD_TOP = 22;
    /** Content width inside the story card. */
    public static final int CONTENT_WIDTH = VIEWPORT_WIDTH - (PAGE_PAD_X * 2) - (CARD_PAD_X * 2); // 338
    public static final double FONT_SIZE = 15.0;
    public static final double LINE_HEIGHT_RATIO = 1.85;
    public static final double LINE_H = FONT_SIZE * LINE_HEIGHT_RATIO; // 27.75
    /** CJK ~1em per glyph at 15px. */
    public static final int CHARS_PER_LINE = Math.max(1, (int) Math.floor(CONTENT_WIDTH / FONT_SIZE)); // 22
    /**
     * Header row: back chevron (~17) + 13px title line, plus marginBottom 16.
     * Title is single-line ellipsis — length ignored.
     */
    public static final double HEADER_H = 17.0 + 16.0;
    /** Solo faction label (dot + text + marginBottom 14). Paired tabs are taller; AI posts are usually solo. */
    public static final double FACTION_LABEL_H = 9.0 + 14.0;

    private CaptureHeightCalculator() {}

    /**
     * @param title ignored for height (ellipsis); retained for call-site clarity
     * @param body  published body
     * @param resolved capture cuts + block budget
     * @param paired whether faction tabs are shown
     * @return CSS Y of each cut (from page top), same length as {@code resolved.splits()}; empty when no splits
     */
    public static List<Double> partHeightsCss(
            String title, String body, CaptureSplitSupport.ResolvedCapture resolved, boolean paired) {
        if (resolved == null || resolved.splits().isEmpty()) return List.of();

        List<String> blocks = CaptureSplitSupport.nonEmptyBlocks(body);
        int usable = Math.min(resolved.captureBlockCount(), blocks.size());
        double chrome = PAGE_PAD_TOP + HEADER_H + (paired ? tabChrome() : FACTION_LABEL_H) + CARD_PAD_TOP;

        List<Double> heights = new ArrayList<>();
        for (int cut : resolved.splits()) {
            int end = Math.min(cut, usable);
            heights.add(chrome + bodyLinesThrough(blocks, end) * LINE_H);
        }
        return heights;
    }

    /**
     * Legacy single-cut height.
     * @deprecated use {@link #partHeightsCss}
     */
    @Deprecated
    public static Double part1HeightCss(String title, String body, Integer splitAfterLine, boolean paired) {
        List<Integer> prop = splitAfterLine == null ? null : List.of(splitAfterLine);
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(body, prop);
        List<Double> hs = partHeightsCss(title, body, r, paired);
        return hs.isEmpty() ? null : hs.get(0);
    }

    private static double bodyLinesThrough(List<String> blocks, int endExclusive1Based) {
        int end = Math.min(endExclusive1Based, blocks.size());
        if (end <= 0) return 0;
        double bodyLines = 0;
        for (int i = 0; i < end; i++) {
            bodyLines += visualLines(blocks.get(i));
        }
        if (end > 1) {
            bodyLines += (end - 1); // pre-wrap newlines between blocks
        }
        return bodyLines;
    }

    /** Faction tab row height approximation when paired. */
    private static double tabChrome() {
        return 9 + 12 + 2 + 16; // padding + font + border + marginBottom ≈ tabs block
    }

    static int visualLines(String block) {
        if (block == null || block.isEmpty()) return 1;
        return Math.max(1, (int) Math.ceil(block.length() / (double) CHARS_PER_LINE));
    }
}
