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
 * Story response DTO (full details).
 * V15.2: Marketing story detail response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("sourcePlatform")
    private String sourcePlatform;

    @JsonProperty("sourceUrl")
    private String sourceUrl;

    @JsonProperty("rawText")
    private String rawText;

    @JsonProperty("category")
    private String category;

    @JsonProperty("relationType")
    private String relationType;

    @JsonProperty("status")
    private String status;

    @JsonProperty("blockedReason")
    private String blockedReason;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("createdAt")
    private Instant createdAt;

    public static StoryResponse from(MarketingSourceStory story) {
        return StoryResponse.builder()
            .id(story.getId())
            .sourcePlatform(story.getSourcePlatform())
            .sourceUrl(story.getSourceUrl())
            .rawText(story.getRawText())
            .category(story.getCategory())
            .relationType(story.getRelationType())
            .status(story.getStatus() != null ? story.getStatus().toString() : null)
            .blockedReason(story.getBlockedReason())
            .createdBy(story.getCreatedBy())
            .createdAt(story.getCreatedAt())
            .build();
    }
}
