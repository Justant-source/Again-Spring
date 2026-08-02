package com.againspring.marketing;

import java.util.List;

/**
 * Candidate CSS Y for X-thread story part1 crop on {@code /community/{id}/read?side=g}.
 * Authoritative cut is still ASM DOM measurement; this formula is verification + fallback.
 *
 * <p>Layout constants mirror {@code frontend/app/community/[id]/read/page.tsx} at viewport 430.
 * Header title uses ellipsis ({@code nowrap}) so title <em>length</em> does not change height —
 * callers may still pass title for API symmetry / future layout changes.
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
     * @param body  published author body
     * @param splitAfterLine 1-based last front-half block; if null, resolved via {@link CaptureSplitSupport}
     * @param paired whether faction tabs are shown (slightly taller chrome)
     * @return CSS Y of the cut (from page top), or null when no split
     */
    public static Double part1HeightCss(String title, String body, Integer splitAfterLine, boolean paired) {
        Integer split = CaptureSplitSupport.resolveSplit(body, splitAfterLine);
        if (split == null) return null;

        List<String> blocks = CaptureSplitSupport.nonEmptyBlocks(body);
        double bodyLines = 0;
        for (int i = 0; i < split; i++) {
            bodyLines += visualLines(blocks.get(i));
        }
        // pre-wrap: one newline between consecutive non-empty blocks
        if (split > 1) {
            bodyLines += (split - 1);
        }

        double chrome = PAGE_PAD_TOP + HEADER_H + (paired ? tabChrome() : FACTION_LABEL_H) + CARD_PAD_TOP;
        return chrome + bodyLines * LINE_H;
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
