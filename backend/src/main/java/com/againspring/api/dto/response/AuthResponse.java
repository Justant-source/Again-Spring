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
 * Auth response DTO for signup/login/guest endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    @JsonProperty("user")
    private UserInfo user;

    @JsonProperty("token")
    private TokenInfo token;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("email")
        private String email;

        @JsonProperty("nickname")
        private String nickname;

        @JsonProperty("isGuest")
        private boolean isGuest;

        @JsonProperty("communicationStyle")
        private String communicationStyle;

        @JsonProperty("mbtiType")
        private String mbtiType;

        @JsonProperty("mbtiProfile")
        private Map<String, Integer> mbtiProfile;

        @JsonProperty("provider")
        private String provider;

        @JsonProperty("onboardingCompletedAt")
        private Instant onboardingCompletedAt;

        @JsonProperty("createdAt")
        private Instant createdAt;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenInfo {
        @JsonProperty("accessToken")
        private String accessToken;

        @JsonProperty("refreshToken")
        private String refreshToken;

        @JsonProperty("expiresIn")
        private long expiresIn; // in seconds
    }
}
