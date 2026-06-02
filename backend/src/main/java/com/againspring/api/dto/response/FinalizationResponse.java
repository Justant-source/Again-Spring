package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FinalizationResponse (V1.5 카톡식)
 * 종료 동의 상태
 */
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class FinalizationResponse {
    private boolean completed;
    @JsonProperty("awaitingPartner")
    private boolean awaitingPartner;

    public static FinalizationResponse from(Object result) {
        // Stub: ChatService.FinalizationResult class removed
        return FinalizationResponse.builder().build();
    }
}
