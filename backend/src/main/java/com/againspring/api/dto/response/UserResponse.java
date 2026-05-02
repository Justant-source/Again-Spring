package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User profile response DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("email")
    private String email;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("communicationStyle")
    private String communicationStyle;

    @JsonProperty("isGuest")
    private boolean isGuest;

    @JsonProperty("onboardingCompleted")
    private boolean onboardingCompleted;

    @JsonProperty("mbtiType")
    private String mbtiType;

    @JsonProperty("mbtiProfile")
    private Map<String, Integer> mbtiProfile;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("createdAt")
    private Instant createdAt;

}
