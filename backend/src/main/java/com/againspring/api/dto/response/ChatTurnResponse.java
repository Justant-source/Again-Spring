package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * ChatTurnResponse (V1.5 카톡식, stub implementation).
 * NOTE: ChatService.ChatTurnResult class removed.
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatTurnResponse {
    private boolean success;
    private UserMessageDto userObject;
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

    public static ChatTurnResponse from(Object result) {
        // Stub: ChatService.ChatTurnResult class removed
        return ChatTurnResponse.builder()
            .success(false)
            .build();
    }

    public static ChatTurnResponse crisis() {
        return ChatTurnResponse.builder()
            .success(false)
            .crisisLevel(1)
            .build();
    }
}
