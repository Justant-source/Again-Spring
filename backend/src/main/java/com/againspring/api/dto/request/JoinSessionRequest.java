package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join session request DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JoinSessionRequest {

    @JsonProperty("nickname")
    private String nickname; // optional for guest

    @JsonProperty("asGuest")
    private Boolean asGuest;
}
