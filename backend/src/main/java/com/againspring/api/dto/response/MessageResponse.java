package com.againspring.api.dto.response;

import com.againspring.domain.Message;
import com.againspring.domain.enums.MessageSender;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MessageResponse (V1.5 카톡식)
 * 전체 메시지 정보 (본인 메시지만)
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MessageResponse {
    private Long id;
    private MessageSender sender;
    private String content;
    private int charCount;
    @JsonProperty("isFinalizeSuggestion")
    private boolean isFinalizeSuggestion;
    @JsonProperty("isPartnerJoinNotice")
    private boolean isPartnerJoinNotice;
    private Instant createdAt;

    public static MessageResponse from(Message msg) {
        return MessageResponse.builder()
            .id(msg.getId())
            .sender(msg.getSender())
            .content(msg.getContent())
            .charCount(msg.getCharCount())
            .isFinalizeSuggestion(msg.getIsFinalizeSuggestion() != null && msg.getIsFinalizeSuggestion())
            .isPartnerJoinNotice(msg.getIsPartnerJoinNotice() != null && msg.getIsPartnerJoinNotice())
            .createdAt(msg.getCreatedAt())
            .build();
    }
}
