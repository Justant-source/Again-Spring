package com.againspring.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Story creation request DTO.
 * V15.2: Marketing story submission.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoryRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Source platform is required")
    private String sourcePlatform;

    private String sourceUrl;

    @NotBlank(message = "Raw text is required")
    private String rawText;

    @NotBlank(message = "Relation type is required")
    private String relationType;
}
