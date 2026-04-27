package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    @Pattern(regexp = "^[IE][NS][TF][JP]$", message = "MBTI 형식이 올바르지 않습니다 (예: INFP)")
    @JsonProperty("mbtiType")
    private String mbtiType;
}
