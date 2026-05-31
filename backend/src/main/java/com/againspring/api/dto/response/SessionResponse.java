package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Session response DTO (full details).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("relationType")
    private String relationType;

    @JsonProperty("category")
    private CategoryInfo category;

    @JsonProperty("status")
    private String status;

    @JsonProperty("currentTurn")
    private int currentTurn;

    @JsonProperty("currentRole")
    private String currentRole;

    @JsonProperty("myRole")
    private String myRole;

    @JsonProperty("partnerNickname")
    private String partnerNickname;

    @JsonProperty("turns")
    private List<TurnInfo> turns;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("finalizeAgreedByA")
    private Boolean finalizeAgreedByA;

    @JsonProperty("finalizeAgreedByB")
    private Boolean finalizeAgreedByB;

    private String reportId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    // V47~: 중·소분류 제거 — 자동 추론 전환.
    public static class CategoryInfo {
        @JsonProperty("major")
        private String major;
        // middle, minor 제거 (V47 — 자동 추론 전환)
        @JsonProperty("customText")
        private String customText;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TurnInfo {
        @JsonProperty("turnNumber")
        private int turnNumber;

        @JsonProperty("role")
        private String role;

        @JsonProperty("mediatorMessage")
        private String mediatorMessage;

        @JsonProperty("myTurn")
        private boolean myTurn;

        @JsonProperty("completed")
        private boolean completed;

        @JsonProperty("createdAt")
        private Instant createdAt;
    }
}
