package com.againspring.aiuser.orchestrator.service.threadplan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drops plan comment candidates whose {@code personaId} is a story-side account
 * (post author and/or partner). Those personas write the post bodies; bystander
 * comments must use other cast members — otherwise the OP appears to reply as a stranger.
 *
 * <p>예외(2026-11): 사연 작성자·상대방의 <b>대댓글(REPLY, parentRef 있음)</b>은 허용한다 — 사람 글쓴이가
 * 댓글에 답하는 모습. 최상위 댓글은 여전히 금지. 자기 댓글에 자기가 답하는 것은 금지하고,
 * 한 글에서 인물당 최대 {@link #DEFAULT_STORY_REPLY_CAP}개(작성자 2·상대방 1은 호출자가 cap으로 지정).</p>
 */
public final class StoryPersonaCommentFilter {
    private StoryPersonaCommentFilter() {}

    /** 사연 당사자 한 명이 한 글에서 달 수 있는 답글 기본 상한(작성자 기준). */
    public static final int DEFAULT_STORY_REPLY_CAP = 2;

    /**
     * Mutates {@code response.items} (or leaves other keys untouched). Returns how many
     * items were removed (including replies whose parent was removed).
     */
    @SuppressWarnings("unchecked")
    public static int stripFromResponse(Map<String, Object> response, Collection<String> excludedPersonaIds) {
        return stripFromResponse(response, excludedPersonaIds, Map.of());
    }

    /** {@code replyCaps}: 인물별 답글 상한 override (없으면 {@link #DEFAULT_STORY_REPLY_CAP}). */
    public static int stripFromResponse(Map<String, Object> response, Collection<String> excludedPersonaIds,
                                        Map<String, Integer> replyCaps) {
        if (response == null || excludedPersonaIds == null || excludedPersonaIds.isEmpty()) return 0;
        Object raw = response.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) return 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?> m) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) copy.put(String.valueOf(e.getKey()), e.getValue());
                }
                items.add(copy);
            }
        }
        int before = items.size();
        List<Map<String, Object>> kept = strip(items, excludedPersonaIds, replyCaps);
        response.put("items", kept);
        return before - kept.size();
    }

    /** Keep order; cascade-drop replies whose parentRef was removed. */
    public static List<Map<String, Object>> strip(List<Map<String, Object>> items,
                                                  Collection<String> excludedPersonaIds) {
        return strip(items, excludedPersonaIds, Map.of());
    }

    public static List<Map<String, Object>> strip(List<Map<String, Object>> items,
                                                  Collection<String> excludedPersonaIds,
                                                  Map<String, Integer> replyCaps) {
        if (items == null || items.isEmpty() || excludedPersonaIds == null || excludedPersonaIds.isEmpty()) {
            return items == null ? List.of() : items;
        }
        Set<String> excluded = new HashSet<>();
        for (String id : excludedPersonaIds) {
            if (id != null && !id.isBlank()) excluded.add(id);
        }
        if (excluded.isEmpty()) return items;

        Set<String> removedRefs = new HashSet<>();
        Map<String, String> personaByRef = new java.util.HashMap<>();
        Map<String, Integer> replyCount = new java.util.HashMap<>();
        List<Map<String, Object>> pass1 = new ArrayList<>(items.size());
        for (Map<String, Object> row : items) {
            if (row == null) continue;
            String personaId = text(row.get("personaId"));
            String ref = text(row.get("ref"));
            String parentRef = text(row.get("parentRef"));
            if (!personaId.isBlank() && excluded.contains(personaId)) {
                boolean keep = !parentRef.isBlank()
                        && !removedRefs.contains(parentRef)
                        && personaByRef.containsKey(parentRef)
                        && !personaId.equals(personaByRef.get(parentRef));
                if (keep) {
                    int cap = replyCaps == null
                            ? DEFAULT_STORY_REPLY_CAP
                            : replyCaps.getOrDefault(personaId, DEFAULT_STORY_REPLY_CAP);
                    int used = replyCount.getOrDefault(personaId, 0);
                    if (used >= cap) keep = false;
                    else replyCount.put(personaId, used + 1);
                }
                if (!keep) {
                    if (!ref.isBlank()) removedRefs.add(ref);
                    continue;
                }
            }
            if (!ref.isBlank()) personaByRef.put(ref, personaId);
            pass1.add(row);
        }

        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (Map<String, Object> row : pass1) {
                String parent = text(row.get("parentRef"));
                String ref = text(row.get("ref"));
                if (!parent.isBlank() && removedRefs.contains(parent) && removedRefs.add(ref)) {
                    progressed = true;
                }
            }
        }

        List<Map<String, Object>> kept = new ArrayList<>(pass1.size());
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : pass1) {
            String ref = text(row.get("ref"));
            if (!ref.isBlank() && removedRefs.contains(ref)) continue;
            if (!ref.isBlank() && !seen.add(ref)) continue;
            kept.add(row);
        }
        return kept;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
