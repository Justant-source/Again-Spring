package com.againspring.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to create a marketing job
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    private Long postId;

    private List<String> targets;

    private boolean autoPublish;
}
