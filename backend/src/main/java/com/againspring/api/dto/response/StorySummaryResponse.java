package com.againspring.api.dto.response;

import com.againspring.domain.marketing.MarketingSourceStory;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Story summary response DTO (lightweight).
 * V15.2: Marketing story list response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorySummaryResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("sourcePlatform")
    private String sourcePlatform;

    @JsonProperty("relationType")
    private String relationType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("createdAt")
    private Instant createdAt;

    public static StorySummaryResponse from(MarketingSourceStory story) {
        return StorySummaryResponse.builder()
            .id(story.getId())
            .sourcePlatform(story.getSourcePlatform())
            .relationType(story.getRelationType())
            .status(story.getStatus() != null ? story.getStatus().toString() : null)
            .createdAt(story.getCreatedAt())
            .build();
    }
}
