package com.againspring.api.dto.response;

import com.againspring.domain.Message;
import com.againspring.domain.enums.MessageSender;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Admin 위기 모니터링 응답 DTO.
 * 안전 정책: 메시지 본문(content)은 절대 포함하지 않음 — 메타데이터만.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrisisMessageResponse {
    private Long messageId;
    private String sessionId;
    private MessageSender sender;
    private Integer crisisLevel;
    private Integer charCount;
    private Instant createdAt;

    public static CrisisMessageResponse from(Message m) {
        return CrisisMessageResponse.builder()
                .messageId(m.getId())
                .sessionId(m.getSessionId())
                .sender(m.getSender())
                .crisisLevel(m.getCrisisLevel())
                .charCount(m.getCharCount())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
