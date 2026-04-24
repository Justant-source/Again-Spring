package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Session list item response DTO (summary).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionListItemResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("relationType")
    private String relationType;

    @JsonProperty("partnerName")
    private String partnerName;

    @JsonProperty("status")
    private String status;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("completedAt")
    private Instant completedAt;
}
