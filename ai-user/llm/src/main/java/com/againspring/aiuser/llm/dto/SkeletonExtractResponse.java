package com.againspring.aiuser.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * persona-diversity-v4 계약 7 — 소스 골격 JSON.
 * 성공 시 {@code ok=true} + 골격 필드, 실패(파싱 실패·필수 키 누락·sequence 3개 미만)
 * 시 {@code ok=false} + {@code reason} — HTTP 400이 아니라 200으로 반환한다(문서 지시).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkeletonExtractResponse {
    private final boolean ok;
    private final String reason;

    private final String category;
    @JsonProperty("author_role") private final String authorRole;
    @JsonProperty("counterpart_role") private final String counterpartRole;
    private final String relationship;
    private final String incident;
    private final List<String> sequence;
    private final String stakes;
    @JsonProperty("author_claim") private final String authorClaim;
    @JsonProperty("counterpart_claim") private final String counterpartClaim;
    private final String emotion;
    @JsonProperty("gray_zone") private final String grayZone;
    @JsonProperty("b_side_viable") private final Boolean bSideViable;
    @JsonProperty("source_example_id") private final Long sourceExampleId;

    public static SkeletonExtractResponse failure(String reason) {
        return SkeletonExtractResponse.builder().ok(false).reason(reason).build();
    }
}
