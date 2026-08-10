package com.againspring.aiuser.orchestrator.service.threadplan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StoryPersonaCommentFilterTest {

    @Test
    void stripsAuthorCommentsAndCascadesReplies() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(row("c1", null, "author", "작성자인 척 남 댓글"));
        items.add(row("r1", "c1", "p2", "대댓글"));
        items.add(row("c2", null, "p3", "정상 댓글"));

        List<Map<String, Object>> kept = StoryPersonaCommentFilter.strip(items, Set.of("author"));

        assertThat(kept).extracting(m -> m.get("ref")).containsExactly("c2");
    }

    @Test
    void stripFromResponseMutatesItems() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", List.of(
                row("c1", null, "author", "자작 댓글"),
                row("c2", null, "p2", "정상")));

        int removed = StoryPersonaCommentFilter.stripFromResponse(response, Set.of("author"));

        assertThat(removed).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        assertThat(items).extracting(m -> m.get("ref")).containsExactly("c2");
    }

    private static Map<String, Object> row(String ref, String parent, String personaId, String body) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ref", ref);
        if (parent != null) m.put("parentRef", parent);
        m.put("personaId", personaId);
        m.put("body", body);
        return m;
    }
}
