package com.againspring.api.dto.response;

import com.againspring.domain.Message;
import com.againspring.service.ChatService;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ChatTurnResponse (V1.5 카톡식)
 * 사용자 메시지 + AI 응답 한 쌍 (분할 메시지 지원)
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatTurnResponse {
    private boolean success;
    private UserMessageDto userMessage;
    private List<MediatorMessageDto> mediatorMessages;
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
        List<MediatorMessageDto> mediatorDtos = null;
        if (result.mediatorMessages() != null && !result.mediatorMessages().isEmpty()) {
            mediatorDtos = result.mediatorMessages().stream()
                .map(m -> MediatorMessageDto.builder()
                    .id(m.getId())
                    .content(m.getContent())
                    .charCount(m.getCharCount())
                    .createdAt(m.getCreatedAt())
                    .build())
                .collect(Collectors.toList());
        }
        return ChatTurnResponse.builder()
            .success(result.success())
            .userMessage(result.userMsg() != null ? UserMessageDto.builder()
                .id(result.userMsg().getId())
                .charCount(result.userMsg().getCharCount())
                .createdAt(result.userMsg().getCreatedAt())
                .build() : null)
            .mediatorMessages(mediatorDtos)
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
