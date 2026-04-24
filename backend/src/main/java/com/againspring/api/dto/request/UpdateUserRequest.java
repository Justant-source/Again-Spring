package com.againspring.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Update user profile request DTO.
 * All fields are optional (PATCH semantics).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("communicationStyle")
    private String communicationStyle;
}
