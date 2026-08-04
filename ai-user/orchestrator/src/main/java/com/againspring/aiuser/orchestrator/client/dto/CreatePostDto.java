package com.againspring.aiuser.orchestrator.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePostDto {
    private String userTitle;
    private String bodyRaw;
    private String category;   // PostCategory enum name: COUPLE/MARRIED/FRIEND/FAMILY/WORK/OTHER
    private String visibility; // "PUBLIC"
    @Builder.Default
    private int jurorCount = 0;  // AI 배심원 모드 숨김 처리 — 0으로 고정
    // ── 원본 비교 기능: 재구성 출처 스냅샷 ───────────────────────────────────────
    /** example_bank.id (재구성 모드 시). null = 일반 생성 */
    private Long sourceExampleId;
    /** 크롤 커뮤니티 식별자 (예: "natepan", "dcinside") */
    private String sourceCommunity;
    /** 크롤 원본 URL */
    private String sourceUrl;
    /** 크롤 원본 제목 */
    private String sourceOriginalTitle;
    /** 크롤 원본 본문 스냅샷 (최대 2000자) */
    private String sourceOriginalBody;
    /**
     * X/IG 캡쳐 컷(1-based). PLAN LLM {@code capture_split_after_lines}.
     */
    private java.util.List<Integer> captureSplitAfterLines;
    /**
     * @deprecated prefer {@link #captureSplitAfterLines}
     */
    @Deprecated
    private Integer captureSplitAfterLine;
    /**
     * IG 훅 제목(원제 복제+의미줄바꿈). PLAN LLM 값.
     * null이면 backend PromoTitleService가 채움.
     */
    private String promoTitle;
}
