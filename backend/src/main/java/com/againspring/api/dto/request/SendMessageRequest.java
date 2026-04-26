package com.againspring.api.dto.request;

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
    private String content;
}
