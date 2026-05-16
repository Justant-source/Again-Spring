package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
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

    /** 임시 비밀번호 발급 후 강제 변경 필요 여부 (V20) */
    @JsonProperty("mustChangePassword")
    private boolean mustChangePassword;

    @JsonProperty("onboardingCompleted")
    private boolean onboardingCompleted;

    @JsonProperty("onboardingCompletedAt")
    private Instant onboardingCompletedAt;

    @JsonProperty("tutorialCompleted")
    private boolean tutorialCompleted;

    @JsonProperty("mediatorDefaultX")
    private Integer mediatorDefaultX;

    @JsonProperty("mbtiType")
    private String mbtiType;

    @JsonProperty("mbtiProfile")
    private Map<String, Integer> mbtiProfile;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("roles")
    private List<String> roles;

    @JsonProperty("termsAgreedAt")
    private Instant termsAgreedAt;

    @JsonProperty("privacyAgreedAt")
    private Instant privacyAgreedAt;

    @JsonProperty("disclaimerAgreedAt")
    private Instant disclaimerAgreedAt;

    @JsonProperty("marketingAgreedAt")
    private Instant marketingAgreedAt;

    @JsonProperty("createdAt")
    private Instant createdAt;

}
