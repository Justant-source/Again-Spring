package com.againspring.service.marketing.image;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Selects key messages from a conversation for marketing screenshots.
 * NOTE: Message class removed due to deletion of mediation code. Stub implementation.
 */
@Component
public class KeyMomentSelector {

    public List<?> select(List<?> messages) {
        // Stub: return empty list
        return new ArrayList<>();
    }

    public List<Map<String, Object>> toRendererPayload(List<?> messages) {
        // Stub: return empty list
        return new ArrayList<>();
    }
}
