package com.againspring.api.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to send a message in a 3-way mediation session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreeWayMessageRequest {

    @NotBlank(message = "Content is required")
    @Size(max = 8000, message = "Content must be at most 8000 characters")
    private String content;

    @NotBlank(message = "Author role is required")
    private String authorRole;
}
