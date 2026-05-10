package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserDetailResponse {
    private String id;
    private String email;
    private String nickname;
    private boolean isGuest;
    private String mbtiType;
    private String communicationStyle;
    private String provider;
    private java.util.List<String> roles;
    private Instant createdAt;
    private Instant deletedAt;
    private Instant onboardingCompletedAt;
    private Instant termsAgreedAt;
    private Instant privacyAgreedAt;
    private Instant disclaimerAgreedAt;
    private Instant marketingAgreedAt;

    // 집계 통계
    private long totalSessions;
    private long completedSessions;
    private long feedbackCount;
    private Instant lastSessionAt;
}
