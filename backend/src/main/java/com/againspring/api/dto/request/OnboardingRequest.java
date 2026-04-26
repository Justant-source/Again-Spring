package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Onboarding request DTO.
 * Either provide 10 Likert answers (10-question path) OR communicationStyle (MBTI path).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {

    @Size(min = 10, max = 10, message = "Exactly 10 answers required")
    @JsonProperty("answers")
    private List<Integer> answers;

    @JsonProperty("communicationStyle")
    private String communicationStyle;

    @JsonProperty("mbtiType")
    private String mbtiType;
}
