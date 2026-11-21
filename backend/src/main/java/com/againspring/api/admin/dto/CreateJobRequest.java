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

    /** Render profile: MarketingRenderProfiles.ALLOWED 중 하나. Nullable; uses env default if not specified. */
    private String renderProfile;

    /**
     * 포맷 실험 arm key(테스트 탭 전용). 지정하면 그 arm의 프로필·대본 스타일·댓글 수·엔드카드를 적용한다.
     * arm이 꺼져 있어도 쓸 수 있지만 autoPublish=true와는 함께 쓸 수 없다.
     */
    private String formatArm;
}
