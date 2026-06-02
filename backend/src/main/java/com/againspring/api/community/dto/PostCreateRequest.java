package com.againspring.api.community.dto;

import com.againspring.domain.enums.PostCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 포스트 생성 요청
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequest {

    @NotBlank(message = "사연 제목은 필수입니다")
    @Size(max = 100, message = "사연 제목은 100자 이하여야 합니다")
    private String userTitle;

    @NotBlank(message = "사연 내용은 필수입니다")
    @Size(max = 600, message = "사연 내용은 600자 이하여야 합니다")
    private String bodyRaw;

    @NotNull(message = "카테고리는 필수입니다")
    private PostCategory category;

    @NotNull(message = "공개 설정은 필수입니다")
    private String visibility; // "PUBLIC" or "PRIVATE"

    @Min(value = 0, message = "심사자 수는 0 이상이어야 합니다")
    @Max(value = 9, message = "심사자 수는 9 이하여야 합니다")
    @Default
    private int jurorCount = 3;

    private String sessionId; // nullable
}
