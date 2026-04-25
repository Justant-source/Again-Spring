package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Session status response DTO (lightweight polling response).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionStatusResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("status")
    private String status;

    @JsonProperty("currentTurn")
    private int currentTurn;

    @JsonProperty("hasPartnerJoined")
    private boolean hasPartnerJoined;

    @JsonProperty("lastUpdatedAt")
    private Instant lastUpdatedAt;
}
