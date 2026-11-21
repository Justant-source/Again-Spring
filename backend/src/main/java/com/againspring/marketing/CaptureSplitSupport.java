package com.againspring.marketing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Marketing capture split helpers — non-empty newline blocks weighted by visual lines.
 * Block cap matches ASM {@code SHORT_POST_MAX_LINES} (=8).
 *
 * <p>SSOT cut list = 1-based indices of the <em>last</em> block in each part except the final
 * part ({@code capture_split_after_lines}). Budget overflow truncates the marketing capture
 * (app body unchanged).
 */
public final class CaptureSplitSupport {

    public static final int SHORT_POST_MAX_BLOCKS = 8;
    /** Visual-line budget per capture part (8 blocks × 3 lines, the legacy card size). */
    public static final int PART_MAX_VISUAL_LINES = 24;
    /** Uncut threshold: budget + 4 lines slack so legacy ≤8-block bodies never newly split at the edge. */
    public static final int NO_CUT_MAX_VISUAL_LINES = PART_MAX_VISUAL_LINES + 4;
    public static final int MAX_PARTS_PER_SIDE = 4;
    public static final int MAX_PARTS_PAIRED_TOTAL = 6;

    private CaptureSplitSupport() {}

    /** Non-empty lines after splitting on any newline. */
    public static List<String> nonEmptyBlocks(String body) {
        List<String> out = new ArrayList<>();
        if (body == null || body.isBlank()) return out;
        for (String line : body.split("\\R", -1)) {
            if (!line.isBlank()) out.add(line);
        }
        return out;
    }

    public static int countBlocks(String body) {
        return nonEmptyBlocks(body).size();
    }

    /**
     * Number of story parts implied by cut list + whether body is present.
     * Empty cuts with {@code captureBlocks > 0} → 1 part.
     */
    public static int partCount(List<Integer> cuts, int captureBlocks) {
        if (captureBlocks <= 0) return 0;
        if (cuts == null || cuts.isEmpty()) return 1;
        return cuts.size() + 1;
    }

    /**
     * Resolve cuts for one side with a part budget.
     *
     * <p>Unit of weight = visual lines ({@link CaptureHeightCalculator#visualLines}). A part holds
     * at most {@link #SHORT_POST_MAX_BLOCKS} blocks and {@link #PART_MAX_VISUAL_LINES} visual lines;
     * a single block over the line budget cannot be cut and becomes its own part.
     *
     * @param proposed LLM cuts (may be null); legacy single int callers wrap as singleton
     * @param maxParts max cards for this side (≤ {@link #MAX_PARTS_PER_SIDE})
     */
    public static ResolvedCapture resolveSplits(String body, List<Integer> proposed, int maxParts) {
        List<String> blocks = nonEmptyBlocks(body);
        int n = blocks.size();
        if (n <= 0) {
            return ResolvedCapture.empty();
        }
        int partsCap = Math.max(1, Math.min(maxParts, MAX_PARTS_PER_SIDE));
        int[] w = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            w[i] = CaptureHeightCalculator.visualLines(blocks.get(i));
            total += w[i];
        }

        // Legacy-shaped bodies (≤8 blocks, ≈ one card) stay uncut; slack avoids new splits at the edge.
        if (n <= SHORT_POST_MAX_BLOCKS && total <= NO_CUT_MAX_VISUAL_LINES && partsCap >= 1) {
            return new ResolvedCapture(List.of(), n);
        }

        // Greedy pack at the full budget: maximal prefix that fits in partsCap parts.
        int usable = 0;
        int parts = 0;
        while (usable < n && parts < partsCap) {
            usable = packEnd(w, usable, n, PART_MAX_VISUAL_LINES);
            parts++;
        }
        if (parts <= 1) {
            return new ResolvedCapture(List.of(), usable);
        }

        List<Integer> cuts = sanitizeProposed(proposed, w, usable, partsCap);
        if (cuts == null) {
            cuts = heuristicCuts(w, usable, parts);
        }
        return new ResolvedCapture(List.copyOf(cuts), usable);
    }

    /** Exclusive end index of the part starting at {@code from} under {@code limit} lines. */
    private static int packEnd(int[] w, int from, int n, int limit) {
        int end = from;
        int lines = 0;
        while (end < n && end - from < SHORT_POST_MAX_BLOCKS && (end == from || lines + w[end] <= limit)) {
            lines += w[end];
            end++;
        }
        return end;
    }

    private static int partsNeeded(int[] w, int usable, int limit) {
        int pos = 0;
        int parts = 0;
        while (pos < usable) {
            pos = packEnd(w, pos, usable, limit);
            parts++;
        }
        return parts;
    }

    private static boolean partFits(int[] w, int from, int to) {
        if (to - from < 1 || to - from > SHORT_POST_MAX_BLOCKS) return false;
        if (to - from == 1) return true;
        int lines = 0;
        for (int i = from; i < to; i++) lines += w[i];
        return lines <= PART_MAX_VISUAL_LINES;
    }

    /**
     * Solo: up to {@link #MAX_PARTS_PER_SIDE} parts.
     * Prefer LLM cuts; else 8-block chunks; truncate past budget.
     */
    public static ResolvedCapture resolveSolo(String body, List<Integer> proposed) {
        return resolveSplits(body, proposed, MAX_PARTS_PER_SIDE);
    }

    /**
     * Paired: author + partner body cards sum ≤ {@link #MAX_PARTS_PAIRED_TOTAL}.
     * Shrink partner first when over budget.
     */
    public static PairedCapture resolvePaired(
            String authorBody, List<Integer> authorProposed,
            String partnerBody, List<Integer> partnerProposed) {
        ResolvedCapture author = resolveSplits(authorBody, authorProposed, MAX_PARTS_PER_SIDE);
        int authorParts = partCount(author.splits(), author.captureBlockCount());
        int partnerBudget = Math.max(0, MAX_PARTS_PAIRED_TOTAL - authorParts);
        if (partnerBudget <= 0 || countBlocks(partnerBody) <= 0) {
            return new PairedCapture(author, ResolvedCapture.empty());
        }
        ResolvedCapture partner = resolveSplits(partnerBody, partnerProposed, partnerBudget);
        return new PairedCapture(author, partner);
    }

    private static List<Integer> sanitizeProposed(List<Integer> proposed, int[] w, int usable, int partsCap) {
        if (proposed == null || proposed.isEmpty()) return null;
        List<Integer> cuts = new ArrayList<>();
        int prev = 0;
        for (Integer cut : proposed) {
            if (cut == null) continue;
            if (cuts.size() >= partsCap - 1) break;
            if (cut <= prev || cut >= usable) continue;
            if (!partFits(w, prev, cut)) continue;
            cuts.add(cut);
            prev = cut;
        }
        if (cuts.isEmpty()) return null;
        // last part must also fit; otherwise fall back to the heuristic
        if (!partFits(w, prev, usable)) return null;
        return cuts;
    }

    /**
     * Balanced cuts: find the smallest per-part line limit that still packs into {@code parts}
     * parts, then fill each part greedily up to that limit.
     */
    private static List<Integer> heuristicCuts(int[] w, int usable, int parts) {
        int limit = PART_MAX_VISUAL_LINES;
        for (int m = 1; m <= PART_MAX_VISUAL_LINES; m++) {
            if (partsNeeded(w, usable, m) <= parts) {
                limit = m;
                break;
            }
        }
        List<Integer> cuts = new ArrayList<>();
        int pos = 0;
        while (pos < usable) {
            pos = packEnd(w, pos, usable, limit);
            if (pos < usable) cuts.add(pos);
        }
        return cuts;
    }

    /** Prefer JSON list; else wrap legacy single INT. */
    public static List<Integer> coalesceProposed(List<Integer> lines, Integer legacySingle) {
        if (lines != null && !lines.isEmpty()) return lines;
        if (legacySingle != null) return List.of(legacySingle);
        return null;
    }

    public record ResolvedCapture(List<Integer> splits, int captureBlockCount) {
        public static ResolvedCapture empty() {
            return new ResolvedCapture(List.of(), 0);
        }

        public boolean isEmpty() {
            return captureBlockCount <= 0;
        }
    }

    public record PairedCapture(ResolvedCapture author, ResolvedCapture partner) {}
}
