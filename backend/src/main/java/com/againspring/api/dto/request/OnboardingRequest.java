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
 * Expects 10 Likert scale answers (1-5 each).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingRequest {

    @NotNull(message = "Answers are required")
    @Size(min = 10, max = 10, message = "Exactly 10 answers required")
    @JsonProperty("answers")
    private List<Integer> answers;
}
