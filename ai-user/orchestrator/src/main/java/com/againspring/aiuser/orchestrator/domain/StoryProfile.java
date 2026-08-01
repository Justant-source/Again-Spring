package com.againspring.aiuser.orchestrator.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable structured view of one source story (plan §7.3).
 * Built once per story and reused for search / matching / generation.
 *
 * <p>Search document packs category · topics · life_context · value_axis · emotional cues
 * into ≤{@value #SEARCH_DOC_MAX} chars (ai-learning {@code POST /embed} truncates at 512).
 */
public record StoryProfile(
        String centralConflict,
        String category,
        List<String> topics,
        Map<String, String> explicitIdentity,
        List<String> lifeContext,
        List<String> valueAxis,
        List<String> timeline,
        List<String> specificDetails,
        List<String> authorKnownFacts,
        List<String> unknowns,
        String sourceRegister,
        List<String> replyAffordances,
        String authorBlindSpot,
        String counterpartReasonablePoint
) {
    public static final int SEARCH_DOC_MAX = 512;

    private static final Map<String, String> EMOTION_NEEDLES = emotionNeedles();

    public StoryProfile {
        centralConflict = nullToEmpty(centralConflict);
        category = nullToEmpty(category);
        topics = copyList(topics);
        explicitIdentity = copyMap(explicitIdentity);
        lifeContext = copyList(lifeContext);
        valueAxis = copyList(valueAxis);
        timeline = copyList(timeline);
        specificDetails = copyList(specificDetails);
        authorKnownFacts = copyList(authorKnownFacts);
        unknowns = copyList(unknowns);
        sourceRegister = nullToEmpty(sourceRegister);
        replyAffordances = copyList(replyAffordances);
        authorBlindSpot = nullToEmpty(authorBlindSpot);
        counterpartReasonablePoint = nullToEmpty(counterpartReasonablePoint);
    }

    /**
     * Compact embedding query ≤{@value #SEARCH_DOC_MAX} characters (Korean BMP-safe via {@link String#length()}).
     * Never exceeds the limit. Core fields (category · topics · life · value · emotional · register)
     * are packed first; soft fields fill remaining budget.
     */
    public String toSearchDocument() {
        StringBuilder sb = new StringBuilder(SEARCH_DOC_MAX);
        appendBudgeted(sb, "category", category.isBlank() ? List.of() : List.of(category));
        appendBudgeted(sb, "register", sourceRegister.isBlank() ? List.of() : List.of(sourceRegister));
        appendBudgeted(sb, "topics", topics);
        appendBudgeted(sb, "life_context", lifeContext);
        appendBudgeted(sb, "value_axis", valueAxis);
        appendBudgeted(sb, "emotional", detectEmotionalCues());
        appendBudgeted(sb, "affordances", replyAffordances);
        if (sb.length() > SEARCH_DOC_MAX) {
            return sb.substring(0, SEARCH_DOC_MAX);
        }
        return sb.toString();
    }

    /** Append {@code label:v1,v2} if budget remains; values clipped individually when needed. */
    private static void appendBudgeted(StringBuilder sb, String label, List<String> values) {
        if (values == null || values.isEmpty()) return;
        int remaining = SEARCH_DOC_MAX - sb.length();
        if (remaining <= 0) return;

        String sep = sb.isEmpty() ? "" : " | ";
        String prefix = sep + label + ":";
        if (prefix.length() >= remaining) return;

        StringBuilder vals = new StringBuilder();
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            String piece = vals.isEmpty() ? v : "," + v;
            if (prefix.length() + vals.length() + piece.length() > remaining) {
                int room = remaining - prefix.length() - vals.length();
                if (room > 1 && vals.isEmpty()) {
                    vals.append(v, 0, Math.min(v.length(), room));
                }
                break;
            }
            vals.append(piece);
        }
        if (vals.isEmpty()) return;
        sb.append(prefix).append(vals);
    }

    /** Emotion needles scanned from conflict · topics · blind-spot (not a schema field). */
    List<String> detectEmotionalCues() {
        String hay = centralConflict + " " + String.join(" ", topics) + " " + authorBlindSpot;
        List<String> hit = new ArrayList<>();
        for (var e : EMOTION_NEEDLES.entrySet()) {
            if (hay.contains(e.getKey()) && !hit.contains(e.getValue())) {
                hit.add(e.getValue());
            }
        }
        return hit;
    }

    private static Map<String, String> emotionNeedles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("배신", "배신감");
        m.put("속았", "배신감");
        m.put("불공정", "불공정");
        m.put("억울", "억울함");
        m.put("분노", "분노");
        m.put("화나", "분노");
        m.put("불안", "불안");
        m.put("걱정", "불안");
        m.put("서운", "서운함");
        m.put("상처", "상처");
        m.put("외로", "외로움");
        m.put("수치", "수치심");
        m.put("창피", "수치심");
        m.put("질투", "질투");
        m.put("후회", "후회");
        m.put("미안", "죄책감");
        m.put("죄책", "죄책감");
        return Map.copyOf(m);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<String> copyList(List<String> in) {
        if (in == null || in.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return List.copyOf(out);
    }

    private static Map<String, String> copyMap(Map<String, String> in) {
        if (in == null || in.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : in.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            String v = Objects.toString(e.getValue(), "").trim();
            if (v.isEmpty()) continue;
            out.put(e.getKey().trim(), v);
        }
        return Map.copyOf(out);
    }
}
