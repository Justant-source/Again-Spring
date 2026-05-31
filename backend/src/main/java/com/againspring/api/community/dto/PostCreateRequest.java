package com.againspring.api.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
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

    @NotBlank(message = "사연 내용은 필수입니다")
    private String bodyRaw;

    @NotNull(message = "카테고리는 필수입니다")
    private String category;

    @NotNull(message = "공개 설정은 필수입니다")
    private String visibility; // "PUBLIC" or "PRIVATE"

    private String sessionId; // nullable
}
