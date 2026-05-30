package com.againspring.llm.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured prompt representation as a list of segments.
 * Each segment has a cache tier, text, and optional role for semantic understanding.
 *
 * §7.3 Reordering Tier Sequence:
 *   [GLOBAL_STATIC]  system.md → gottman/four_horsemen.md → nvc/four_steps.md → chat/{solo,duo}_chat.md
 *   [SESSION_STATIC] <mediator_style> → relations/<type>.md → <user_profile>(들)
 *   [HISTORY]        <conversation_history> (message-by-message segments)
 *   [DYNAMIC]        <psychology_feedback> + Phase-D fragments + <duo_balance> + (duo: partner_onboarding/duo_specific_rules) + <current_user_message> + _response_instructions.md
 *
 * flatten() guarantees byte-for-byte equivalence with legacy String assembleSoloTurn()/assembleDuoTurn().
 */
@Getter
@AllArgsConstructor
@ToString
public class StructuredPrompt {
    private final List<PromptSegment> segments;

    public StructuredPrompt() {
        this.segments = new ArrayList<>();
    }

    /**
     * Create empty structured prompt.
     */
    public static StructuredPrompt empty() {
        return new StructuredPrompt(new ArrayList<>());
    }

    /**
     * Add a segment to this prompt.
     */
    public void add(PromptSegment segment) {
        segments.add(segment);
    }

    /**
     * Add a segment by tier and text.
     */
    public void add(CacheTier tier, String text) {
        segments.add(new PromptSegment(tier, text));
    }

    /**
     * Add a segment by tier, text, and role.
     */
    public void add(CacheTier tier, String text, SegmentRole role) {
        segments.add(new PromptSegment(tier, text, role));
    }

    /**
     * Add a segment only if text is not empty.
     */
    public void addIfNotEmpty(CacheTier tier, String text) {
        if (text != null && !text.isEmpty()) {
            segments.add(new PromptSegment(tier, text));
        }
    }

    /**
     * Add a segment only if text is not empty, with role.
     */
    public void addIfNotEmpty(CacheTier tier, String text, SegmentRole role) {
        if (text != null && !text.isEmpty()) {
            segments.add(new PromptSegment(tier, text, role));
        }
    }

    /**
     * Flatten to a single string by concatenating all segment texts.
     * Guarantees byte-for-byte equivalence with legacy String-based assembly.
     */
    public String flatten() {
        StringBuilder sb = new StringBuilder();
        for (PromptSegment seg : segments) {
            sb.append(seg.getText());
        }
        return sb.toString();
    }

    /**
     * Get unmodifiable view of segments.
     */
    public List<PromptSegment> getSegmentsReadOnly() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * Get segments filtered by cache tier.
     */
    public List<PromptSegment> getSegmentsByTier(CacheTier tier) {
        return segments.stream()
                .filter(seg -> seg.getTier() == tier)
                .toList();
    }

    /**
     * Get segments filtered by role.
     */
    public List<PromptSegment> getSegmentsByRole(SegmentRole role) {
        return segments.stream()
                .filter(seg -> seg.getRole() == role)
                .toList();
    }

    /**
     * Check if prompt is empty.
     */
    public boolean isEmpty() {
        return segments.isEmpty() || flatten().trim().isEmpty();
    }

    /**
     * Get total segment count.
     */
    public int segmentCount() {
        return segments.size();
    }

    /**
     * Get total character count of flattened prompt.
     */
    public int charCount() {
        return flatten().length();
    }
}
