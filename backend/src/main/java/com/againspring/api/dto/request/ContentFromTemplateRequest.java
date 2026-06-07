package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentFromTemplateRequest {
    @NotBlank
    private String postId;

    private String platform;

    private Map<String, String> variables;
}
