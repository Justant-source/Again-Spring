package com.againspring.api.community.dto;

import com.againspring.domain.enums.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "사연 제목은 필수입니다")
    @Size(max = 100, message = "사연 제목은 100자 이하여야 합니다")
    private String userTitle;

    @NotBlank(message = "사연 내용은 필수입니다")
    @Size(max = 1000, message = "사연 내용은 1000자 이하여야 합니다")
    private String bodyRaw;

    @NotNull(message = "카테고리는 필수입니다")
    private PostCategory category;

    @NotNull(message = "공개 설정은 필수입니다")
    private String visibility; // "PUBLIC" or "PRIVATE"

    private String sessionId; // nullable

    // ── 원본 비교 기능: 재구성 출처 스냅샷 (AI 봇 전용, 일반 사용자는 null) ──────────────
    private Long sourceExampleId;
    private String sourceCommunity;
    private String sourceUrl;
    private String sourceOriginalTitle;
    private String sourceOriginalBody;

    /**
     * X/IG 캡쳐 컷 목록(1-based). AI PLAN {@code capture_split_after_lines}.
     * null이면 마케팅 잡 생성 시 휴리스틱.
     */
    @com.fasterxml.jackson.annotation.JsonAlias({"capture_split_after_lines"})
    private java.util.List<Integer> captureSplitAfterLines;

    /**
     * @deprecated prefer {@link #captureSplitAfterLines}; single cut promoted to one-element list.
     */
    @Deprecated
    private Integer captureSplitAfterLine;

    /**
     * IG 훅 제목(원제 복제+의미줄바꿈). AI PLAN이 전달하면 저장 후 PromoTitleService skip.
     */
    @com.fasterxml.jackson.annotation.JsonAlias({"promo_title"})
    @Size(max = 500, message = "promoTitle은 500자 이하여야 합니다")
    private String promoTitle;

    /**
     * 메타포 일러스트 ID (60종). AI PLAN 매칭값. 일반 사용자는 null.
     */
    @com.fasterxml.jackson.annotation.JsonAlias({"metaphor_id"})
    @Size(max = 64, message = "metaphorId는 64자 이하여야 합니다")
    private String metaphorId;

    /**
     * 메타포 일러스트 ID 목록 (3-5개, 첫번째 = 대표). AI PLAN 매칭값.
     */
    @com.fasterxml.jackson.annotation.JsonAlias({"metaphor_ids"})
    @Size(max = 5, message = "metaphorIds는 5개 이하여야 합니다")
    private java.util.List<@Size(max = 64, message = "각 metaphor_id는 64자 이하여야 합니다") String> metaphorIds;
}
