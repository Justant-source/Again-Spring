package com.againspring.api.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a 3-way mediation session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreeWayCreateRequest {

    @NotBlank(message = "Category is required")
    @Size(max = 50, message = "Category must be at most 50 characters")
    private String category;
}
