package com.againspring.service.marketing.image;

import com.againspring.domain.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Selects key messages from a conversation for marketing screenshots.
 * Phase 1: deterministic heuristic — first USER_A, longest AI response, last message.
 */
@Component
public class KeyMomentSelector {

    public List<Message> select(List<Message> messages) {
        if (messages.isEmpty()) return messages;
        if (messages.size() <= 3) return messages;

        List<Message> result = new ArrayList<>();

        // First USER_A message (establishes the conflict framing)
        messages.stream()
                .filter(m -> "USER_A".equals(m.getSender().name()))
                .findFirst()
                .ifPresent(result::add);

        // Longest AI/mediator response (most insight-dense)
        messages.stream()
                .filter(m -> !"USER_A".equals(m.getSender().name()) && !"USER_B".equals(m.getSender().name()))
                .max(Comparator.comparingInt(m -> m.getContent() != null ? m.getContent().length() : 0))
                .filter(m -> !result.contains(m))
                .ifPresent(result::add);

        // Last message (resolution or closing)
        Message last = messages.get(messages.size() - 1);
        if (!result.contains(last)) result.add(last);

        result.sort(Comparator.comparingLong(Message::getId));
        return result;
    }

    public List<Map<String, Object>> toRendererPayload(List<Message> messages) {
        return messages.stream()
                .map(m -> Map.<String, Object>of(
                        "sender", m.getSender().name(),
                        "content", m.getContent() != null ? m.getContent() : "",
                        "createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
                ))
                .collect(Collectors.toList());
    }
}
