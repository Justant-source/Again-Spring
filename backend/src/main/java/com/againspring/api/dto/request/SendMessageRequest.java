package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SendMessageRequest (V1.5 카톡식)
 */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class SendMessageRequest {
    @NotBlank(message = "메시지 내용을 입력해 주세요")
    private String content;
}
