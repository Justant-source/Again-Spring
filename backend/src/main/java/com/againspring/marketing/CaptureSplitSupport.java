package com.againspring.marketing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Marketing capture split helpers — non-empty newline blocks as the unit.
 * Threshold matches ASM {@code SHORT_POST_MAX_LINES} (=8).
 *
 * <p>SSOT cut list = 1-based indices of the <em>last</em> block in each part except the final
 * part ({@code capture_split_after_lines}). Budget overflow truncates the marketing capture
 * (app body unchanged).
 */
public final class CaptureSplitSupport {

    public static final int SHORT_POST_MAX_BLOCKS = 8;
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
     * @param proposed LLM cuts (may be null); legacy single int callers wrap as singleton
     * @param maxParts max cards for this side (≤ {@link #MAX_PARTS_PER_SIDE})
     */
    public static ResolvedCapture resolveSplits(String body, List<Integer> proposed, int maxParts) {
        int n = countBlocks(body);
        if (n <= 0) {
            return ResolvedCapture.empty();
        }
        int partsCap = Math.max(1, Math.min(maxParts, MAX_PARTS_PER_SIDE));
        int budgetBlocks = partsCap * SHORT_POST_MAX_BLOCKS;
        int usable = Math.min(n, budgetBlocks);

        if (usable <= SHORT_POST_MAX_BLOCKS) {
            return new ResolvedCapture(List.of(), usable);
        }

        List<Integer> cuts = sanitizeProposed(proposed, usable, partsCap);
        if (cuts == null) {
            cuts = heuristicCuts(usable, partsCap);
        }
        return new ResolvedCapture(List.copyOf(cuts), usable);
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

    /**
     * Legacy single-cut API — first cut only, or null when short / single part.
     * @deprecated use {@link #resolveSolo(String, List)}
     */
    @Deprecated
    public static Integer resolveSplit(String body, Integer proposed) {
        List<Integer> prop = proposed == null ? null : List.of(proposed);
        ResolvedCapture r = resolveSolo(body, prop);
        if (r.splits().isEmpty()) return null;
        return r.splits().get(0);
    }

    private static List<Integer> sanitizeProposed(List<Integer> proposed, int usable, int partsCap) {
        if (proposed == null || proposed.isEmpty()) return null;
        List<Integer> raw = new ArrayList<>();
        for (Integer p : proposed) {
            if (p == null) continue;
            raw.add(p);
        }
        if (raw.isEmpty()) return null;

        List<Integer> cuts = new ArrayList<>();
        int prev = 0;
        for (int cut : raw) {
            if (cuts.size() >= partsCap - 1) break;
            if (cut <= prev || cut >= usable) continue;
            // each part ≤ SHORT_POST_MAX_BLOCKS
            if (cut - prev > SHORT_POST_MAX_BLOCKS) continue;
            cuts.add(cut);
            prev = cut;
        }
        if (cuts.isEmpty()) return null;
        // last part must be non-empty and ≤ SHORT_POST_MAX_BLOCKS
        int lastSize = usable - cuts.get(cuts.size() - 1);
        if (lastSize < 1 || lastSize > SHORT_POST_MAX_BLOCKS) {
            // try to keep cuts but clamp usable is fixed; reject and heuristic
            return null;
        }
        // first part size
        if (cuts.get(0) < 1 || cuts.get(0) > SHORT_POST_MAX_BLOCKS) return null;
        for (int i = 1; i < cuts.size(); i++) {
            int size = cuts.get(i) - cuts.get(i - 1);
            if (size < 1 || size > SHORT_POST_MAX_BLOCKS) return null;
        }
        return cuts;
    }

    /** Even-ish chunks of at most SHORT_POST_MAX_BLOCKS within usable. */
    private static List<Integer> heuristicCuts(int usable, int partsCap) {
        int parts = Math.min(partsCap, (usable + SHORT_POST_MAX_BLOCKS - 1) / SHORT_POST_MAX_BLOCKS);
        if (parts <= 1) return List.of();
        List<Integer> cuts = new ArrayList<>();
        int base = usable / parts;
        int rem = usable % parts;
        int end = 0;
        for (int i = 0; i < parts - 1; i++) {
            int size = base + (i < rem ? 1 : 0);
            if (size > SHORT_POST_MAX_BLOCKS) size = SHORT_POST_MAX_BLOCKS;
            if (size < 1) size = 1;
            end += size;
            if (end >= usable) break;
            cuts.add(end);
        }
        // ensure last part fits
        while (!cuts.isEmpty() && usable - cuts.get(cuts.size() - 1) > SHORT_POST_MAX_BLOCKS) {
            int last = cuts.get(cuts.size() - 1);
            int next = last + SHORT_POST_MAX_BLOCKS;
            if (next >= usable) break;
            cuts.add(next);
            if (cuts.size() >= partsCap - 1) break;
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
