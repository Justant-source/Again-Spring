package com.againspring.api.dto.response;

import com.againspring.domain.enums.ConflictType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for full report.
 * GET /api/reports/{reportId} returns this.
 * Matches shared/types/report.ts structure 1:1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {

    private String id;

    private String sessionId;

    private ConflictType conflictType;

    @JsonProperty("isSoloMode")
    private boolean isSoloMode;

    private ParticipantSnapshot participantA;

    private ParticipantSnapshot participantB;

    private ContributionRatioResponse contributionRatio;

    private NeedsMapResponse needsMap;


    private NVCScriptsResponse nvcScripts;

    private HorsemenObservationResponse horsemenObservation;

    private List<String> repairSuggestions;

    private String aPatternFeedback;

    private String suggestedApproach;

    private String inviteAgainCTA;

    private Instant createdAt;

    /**
     * Embedded DTO: Participant snapshot from report.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantSnapshot {
        private String userId;
        private String nicknameSnapshot;
        private String guestName;
    }

    /**
     * Embedded DTO: Contribution ratio.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributionRatioResponse {
        private int a;
        private int b;
        private RatioLabel label;
        private String clippedFrom;
        private String rationale;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RatioLabel {
            private String a;
            private String b;
        }
    }

    /**
     * Embedded DTO: Needs map with positions.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NeedsMapResponse {
        private String axisX;
        private String axisXLabel;
        private String axisY;
        private String axisYLabel;
        private Position positionA;
        private Position positionB;
        private String interpretation;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Position {
            private int x;
            private int y;
        }
    }

    /**
     * Embedded DTO: NVC scripts.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NVCScriptsResponse {
        private NVCScript aToB;
        private NVCScript bToA;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class NVCScript {
            private String observation;
            private String feeling;
            private String need;
            private String request;
        }
    }

    /**
     * Embedded DTO: 4 Horsemen observation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HorsemenObservationResponse {
        private HorsemenItemResponse criticism;
        private HorsemenItemResponse defensiveness;
        private HorsemenItemResponse contempt;
        private HorsemenItemResponse stonewalling;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class HorsemenItemResponse {
            private int score; // 0-10: 0=none, 3=low, 6=medium, 9=high
            private boolean detected;
        }
    }
}
