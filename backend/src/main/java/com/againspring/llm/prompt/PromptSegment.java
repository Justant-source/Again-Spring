package com.againspring.llm.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * A single segment of a structured prompt.
 * Each segment has a cache tier, text content, and optional role/metadata.
 */
@Getter
@AllArgsConstructor
@ToString
public class PromptSegment {
    private final CacheTier tier;
    private final String text;
    private final SegmentRole role;
    private final String metadata;  // Optional: for debugging or role-specific info

    public PromptSegment(CacheTier tier, String text) {
        this(tier, text, null, null);
    }

    public PromptSegment(CacheTier tier, String text, SegmentRole role) {
        this(tier, text, role, null);
    }

    /**
     * Create a segment with GLOBAL_STATIC tier.
     */
    public static PromptSegment global(String text) {
        return new PromptSegment(CacheTier.GLOBAL_STATIC, text);
    }

    /**
     * Create a segment with GLOBAL_STATIC tier and role.
     */
    public static PromptSegment global(String text, SegmentRole role) {
        return new PromptSegment(CacheTier.GLOBAL_STATIC, text, role);
    }

    /**
     * Create a segment with SESSION_STATIC tier.
     */
    public static PromptSegment session(String text) {
        return new PromptSegment(CacheTier.SESSION_STATIC, text);
    }

    /**
     * Create a segment with SESSION_STATIC tier and role.
     */
    public static PromptSegment session(String text, SegmentRole role) {
        return new PromptSegment(CacheTier.SESSION_STATIC, text, role);
    }

    /**
     * Create a segment with HISTORY tier.
     */
    public static PromptSegment history(String text) {
        return new PromptSegment(CacheTier.HISTORY, text);
    }

    /**
     * Create a segment with HISTORY tier and role.
     */
    public static PromptSegment history(String text, SegmentRole role) {
        return new PromptSegment(CacheTier.HISTORY, text, role);
    }

    /**
     * Create a segment with DYNAMIC tier.
     */
    public static PromptSegment dynamic(String text) {
        return new PromptSegment(CacheTier.DYNAMIC, text);
    }

    /**
     * Create a segment with DYNAMIC tier and role.
     */
    public static PromptSegment dynamic(String text, SegmentRole role) {
        return new PromptSegment(CacheTier.DYNAMIC, text, role);
    }

    /**
     * Factory method for explicit tier/text.
     */
    public static PromptSegment of(CacheTier tier, String text) {
        return new PromptSegment(tier, text);
    }

    /**
     * Factory method for explicit tier/text/role.
     */
    public static PromptSegment of(CacheTier tier, String text, SegmentRole role) {
        return new PromptSegment(tier, text, role);
    }
}
