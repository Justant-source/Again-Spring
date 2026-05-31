package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create session request DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {

    // 사용자가 대분류 선택 화면에서 선택 — 필수 (V47~: 중·소분류 제거, 대분류는 유지)
    @NotBlank(message = "관계 유형을 선택해 주세요")
    @JsonProperty("relationType")
    private String relationType; // couple, marriage, friend, family, parent_child, korean_specific, work

    @JsonProperty("category")
    private CategoryRequest category;

    @JsonProperty("description")
    private String description; // optional, may be scanned for keywords

    @JsonProperty("soloMode")
    private Boolean soloMode; // default true (Solo가 메인 동선, V1.5)

    @Min(0)
    @Max(100)
    @JsonProperty("mediatorStyleX")
    private Integer mediatorStyleX; // 팩트(0) ↔ 공감(100), 기본값 50

    @Min(0)
    @Max(100)
    @JsonProperty("mediatorStyleY")
    private Integer mediatorStyleY; // 경청(0) ↔ 능동(100), 기본값 50

    // V47~: CategoryRequest 단순화 — 중·소분류 제거.
    // 기존 API 호환을 위해 클래스는 잔존하되 실제로는 사용되지 않음.
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRequest {
        @JsonProperty("majorId")
        private String majorId;
        // middleId, minorId 제거 (V47 — 자동 추론 전환)
        @JsonProperty("customText")
        private String customText;
    }
}
