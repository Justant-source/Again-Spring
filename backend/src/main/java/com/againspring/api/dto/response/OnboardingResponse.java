package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Onboarding response DTO.
 * Returns communication style and style info.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardingResponse {

    @JsonProperty("communicationStyle")
    private String communicationStyle;

    @JsonProperty("styleInfo")
    private StyleInfo styleInfo;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StyleInfo {
        @JsonProperty("emoji")
        private String emoji;

        @JsonProperty("label")
        private String label;

        @JsonProperty("description")
        private String description;

        @JsonProperty("strengths")
        private List<String> strengths;

        @JsonProperty("caution")
        private List<String> caution;
    }
}
