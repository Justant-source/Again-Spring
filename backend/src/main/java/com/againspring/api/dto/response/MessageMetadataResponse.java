package com.againspring.api.dto.response;

import com.againspring.domain.enums.MessageSender;
import com.againspring.service.ChatService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * MessageMetadataResponse (V1.5 카톡식)
 * 상대 메시지 메타데이터만 (content 절대 미포함 — 격리 원칙)
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MessageMetadataResponse {
    private Long id;
    private MessageSender sender;
    private int charCount;
    private Instant createdAt;

    public static MessageMetadataResponse from(ChatService.MessageMetadata meta) {
        return MessageMetadataResponse.builder()
            .id(meta.id())
            .sender(meta.sender())
            .charCount(meta.charCount())
            .createdAt(meta.createdAt())
            .build();
    }
}
