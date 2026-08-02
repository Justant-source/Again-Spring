package com.againspring.marketing;

import java.util.ArrayList;
import java.util.List;

/**
 * Marketing capture split helpers — non-empty newline blocks as the unit.
 * Threshold matches ASM {@code SHORT_POST_MAX_LINES} (=12).
 */
public final class CaptureSplitSupport {

    public static final int SHORT_POST_MAX_BLOCKS = 12;

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
     * Prefer a valid LLM/stored split; otherwise heuristic {@code round(n * 0.6)} clamped so both
     * halves are non-empty. Returns null when {@code n ≤ 12}.
     */
    public static Integer resolveSplit(String body, Integer proposed) {
        int n = countBlocks(body);
        if (n <= SHORT_POST_MAX_BLOCKS) return null;
        if (proposed != null && proposed >= 1 && proposed < n) return proposed;
        int split = (int) Math.round(n * 0.6);
        if (split < 1) split = 1;
        if (split >= n) split = n - 1;
        return split;
    }
}
