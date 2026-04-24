package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Boolean soloMode; // default false

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRequest {
        @JsonProperty("major")
        private String major;

        @JsonProperty("middle")
        private String middle;

        @JsonProperty("minor")
        private String minor;

        @JsonProperty("customMinor")
        private String customMinor;
    }
}
