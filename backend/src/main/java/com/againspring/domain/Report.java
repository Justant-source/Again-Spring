package com.againspring.domain;

import com.againspring.domain.enums.ConflictType;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

/**
 * 리포트 엔티티 (MariaDB JPA)
 * 세션 완료 후 LLM이 생성한 분석 결과
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @Column(length = 32)
    private String id;

    @Column(length = 32, unique = true, nullable = false)
    private String sessionId;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private Participant participantA;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private Participant participantB;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ConflictType conflictType;

    @Column
    private Boolean soloMode;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private ContributionRatio contributionRatio;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private NeedsMap needsMap;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private FourHorsemenAnalysis fourHorsemen;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private NVCScripts nvcScripts;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private List<String> repairSuggestions;

    @Column(length = 50)
    private String llmProvider;

    @Column
    private Integer llmCallCount;

    @Column
    private Long generationDurationMs;

    @Column(columnDefinition = "TEXT")
    private String aPatternFeedback;

    @Column(columnDefinition = "TEXT")
    private String suggestedApproach;

    @Column(columnDefinition = "TEXT")
    private String inviteAgainCTA;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 참여자 정보 임베디드
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Getter
    @Setter
    public static class Participant {
        public String userId;
        public String nicknameSnapshot;
        public String guestName;
    }

    /**
     * 화해 기여도 임베디드
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Getter
    @Setter
    public static class ContributionRatio {
        public Integer a;
        public Integer b;
        public RatioLabel label;
        public String clippedFrom;
        public String rationale;

        /**
         * 기여도 라벨
         */
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @Getter
        @Setter
        public static class RatioLabel {
            public String a;
            public String b;
        }
    }

    /**
     * 니즈 맵 임베디드 (2축 좌표)
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Getter
    @Setter
    public static class NeedsMap {
        public String axisX;
        public String axisXLabel;
        public String axisY;
        public String axisYLabel;
        public Position positionA;
        public Position positionB;
        public String interpretation;

        /**
         * 위치 좌표
         */
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @Getter
        @Setter
        public static class Position {
            public Integer x;
            public Integer y;
        }
    }

    /**
     * 고트만 4기사 분석 임베디드
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Getter
    @Setter
    public static class FourHorsemenAnalysis {
        public HorsemenItem criticism;
        public HorsemenItem defensiveness;
        public HorsemenItem contempt;
        public HorsemenItem stonewalling;

        /**
         * 고트만 기사 항목
         */
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @Getter
        @Setter
        public static class HorsemenItem {
            public Boolean detected;
            public String intensity;
            public List<String> examples;
        }
    }

    /**
     * NVC 스크립트 임베디드
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Getter
    @Setter
    public static class NVCScripts {
        public NVCScript aToB;
        public NVCScript bToA;

        /**
         * NVC 스크립트 항목
         */
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        @Getter
        @Setter
        public static class NVCScript {
            public String observation;
            public String feeling;
            public String need;
            public String request;
        }
    }
}
