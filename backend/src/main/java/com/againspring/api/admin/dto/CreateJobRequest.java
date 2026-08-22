package com.againspring.api.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {

    @NotNull(message = "postId는 필수입니다")
    private String postId;

    @NotEmpty(message = "targets는 최소 1개 이상이어야 합니다")
    private List<String> targets;

    private boolean autoPublish;

    /** Render profile: "marketing_fast" | "marketing_v2". Nullable; uses env default if not specified. */
    private String renderProfile;
}
