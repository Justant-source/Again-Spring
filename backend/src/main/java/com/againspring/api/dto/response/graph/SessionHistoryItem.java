package com.againspring.api.dto.response.graph;

import com.againspring.domain.enums.ConflictType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관계별 세션 이력 항목
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionHistoryItem {

    private String sessionId;

    private ConflictType conflictType; // factual, difference, mixed

    private Instant createdAt;

}
