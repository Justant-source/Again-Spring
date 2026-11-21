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
    /** 원문 글의 성격·화자 태도(고민 상담/분노 토로/웃긴 썰 등). 선택 키 — null이면 응답에서 생략. */
    private final String tone;
    /**
     * Lombok의 기본 getter-이름 맹글링(연속 대문자 "BS" → "bs")이 필드명 mangling과
     * 어긋나 Jackson이 이 필드를 두 개의 서로 다른 프로퍼티(필드 쪽 "b_side_viable",
     * getter 쪽 "bsideViable")로 인식하는 버그가 있었다(persona-diversity-v4 결함).
     * onMethod_로 getter 자체에 @JsonProperty를 붙여 두 접근자가 같은 이름으로
     * 병합되게 한다 — 출력 키는 "b_side_viable" 하나만 나와야 한다.
     */
    @Getter(onMethod_ = @__(@JsonProperty("b_side_viable")))
    private final Boolean bSideViable;
    @JsonProperty("source_example_id") private final Long sourceExampleId;
    /**
     * 원문에 작성자와 특정 상대 사이의 실제 사건·충돌이 있는가. 누락 시 true(구 골격 호환).
     * false면 나머지 필드는 비어 있을 수 있다(ok=true 유효 응답).
     */
    @Getter(onMethod_ = @__(@JsonProperty("has_conflict")))
    private final Boolean hasConflict;
    /** 읽는 사람 다수가 공감할 쪽: AUTHOR | PARTNER | SPLIT. 누락·이상값이면 SPLIT. */
    @JsonProperty("verdict_lean") private final String verdictLean;

    public static SkeletonExtractResponse failure(String reason) {
        return SkeletonExtractResponse.builder().ok(false).reason(reason).build();
    }
}
