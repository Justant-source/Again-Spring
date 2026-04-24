package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create session response DTO (with invite token).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSessionResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("inviteToken")
    private String inviteToken;

    @JsonProperty("inviteUrl")
    private String inviteUrl;

    @JsonProperty("status")
    private String status;

    @JsonProperty("currentTurn")
    private int currentTurn;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("expiresAt")
    private Instant expiresAt;
}
