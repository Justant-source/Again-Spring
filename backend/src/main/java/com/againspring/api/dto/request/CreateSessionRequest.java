package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Relation type is required")
    @JsonProperty("relationType")
    private String relationType; // couple, marriage, friend, family, parent_child

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRequest {
        @JsonProperty("majorId")
        private String majorId;

        @JsonProperty("middleId")
        private String middleId;

        @JsonProperty("minorId")
        private String minorId;

        @JsonProperty("customText")
        private String customText;
    }
}
