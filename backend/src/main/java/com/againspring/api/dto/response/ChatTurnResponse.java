package com.againspring.api.dto.response;

import com.againspring.domain.Message;
import com.againspring.service.ChatService;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ChatTurnResponse (V1.5 카톡식)
 * 사용자 메시지 + AI 응답 한 쌍
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatTurnResponse {
    private boolean success;
    private UserMessageDto userMessage;
    private MediatorMessageDto mediatorMessage;
    private boolean finalizeSuggested;
    private Integer crisisLevel;

    @Getter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class UserMessageDto {
        private Long id;
        private int charCount;
        private Instant createdAt;
    }

    @Getter @Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class MediatorMessageDto {
        private Long id;
        private String content;
        private int charCount;
        private Instant createdAt;
    }

    public static ChatTurnResponse from(ChatService.ChatTurnResult result) {
        return ChatTurnResponse.builder()
            .success(result.success())
            .userMessage(result.userMsg() != null ? UserMessageDto.builder()
                .id(result.userMsg().getId())
                .charCount(result.userMsg().getCharCount())
                .createdAt(result.userMsg().getCreatedAt())
                .build() : null)
            .mediatorMessage(result.mediatorMsg() != null ? MediatorMessageDto.builder()
                .id(result.mediatorMsg().getId())
                .content(result.mediatorMsg().getContent())
                .charCount(result.mediatorMsg().getCharCount())
                .createdAt(result.mediatorMsg().getCreatedAt())
                .build() : null)
            .finalizeSuggested(result.finalizeSuggested())
            .crisisLevel(result.crisisLevel())
            .build();
    }

    public static ChatTurnResponse crisis() {
        return ChatTurnResponse.builder()
            .success(false)
            .crisisLevel(1)
            .build();
    }
}
