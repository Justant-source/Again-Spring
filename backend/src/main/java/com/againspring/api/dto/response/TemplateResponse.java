package com.againspring.api.dto.response;

import com.againspring.domain.marketing.MarketingContentTemplate;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateResponse {
    private Long id;
    private String platform;
    private String name;
    private String bodyTemplate;
    private String variablesJson;
    private Boolean isActive;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static TemplateResponse from(MarketingContentTemplate t) {
        return TemplateResponse.builder()
                .id(t.getId())
                .platform(t.getPlatform() != null ? t.getPlatform().name() : null)
                .name(t.getName())
                .bodyTemplate(t.getBodyTemplate())
                .variablesJson(t.getVariablesJson())
                .isActive(t.getIsActive())
                .createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
