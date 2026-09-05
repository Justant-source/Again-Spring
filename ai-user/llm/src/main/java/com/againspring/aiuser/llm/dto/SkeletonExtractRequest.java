package com.againspring.aiuser.llm.dto;

import lombok.Data;

/**
 * WP2 소스 골격 추출 요청 ({@code POST /v2/extract-skeleton}).
 * persona-diversity-v4 계약 7 — 원문 문장을 그대로 담지 않는 "뼈대"만 추출한다.
 */
@Data
public class SkeletonExtractRequest {
    /** example_bank.id — 응답에 그대로 반환되어 감사/dedup에 쓰인다. */
    private Long sourceExampleId;
    /** 광장 카테고리 힌트 (WORK/COUPLE/FRIEND/FAMILY/MARRIED/OTHER). */
    private String category;
    private String title;
    private String content;
    private String correlationId;
}
