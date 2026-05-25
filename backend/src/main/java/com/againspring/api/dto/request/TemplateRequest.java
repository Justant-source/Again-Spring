package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRequest {
    @NotBlank
    private String platform;

    @NotBlank
    @Size(max = 120)
    private String name;

    @NotBlank
    private String bodyTemplate;

    private String variablesJson;

    private Boolean isActive;
}
