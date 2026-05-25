package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentFromTemplateRequest {
    @NotNull
    private Long simulationId;

    private String platform;

    private Map<String, String> variables;
}
