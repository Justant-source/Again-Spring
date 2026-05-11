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

    /** V22: 중재자 톤 기본값 X (0~100). null이면 미변경, 명시적 -1이면 reset(NULL 저장). */
    @JsonProperty("mediatorDefaultX")
    private Integer mediatorDefaultX;
}
