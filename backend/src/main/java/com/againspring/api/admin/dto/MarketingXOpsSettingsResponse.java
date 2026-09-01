package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingXOpsSettingsService;
import com.againspring.marketing.XPersonaLearnService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingXOpsSettingsResponse {

    private String morningTime;
    private String nightTime;
    private int storyScoopsPerDay;
    private int outboundDailyCap;
    private int inboundDailyCap;
    private int inboundPerPostCap;
    private int hotMinReplies;
    private int hotMaxAgeHours;
    private boolean ritualEnabled;
    private boolean inboundEnabled;
    private boolean outboundEnabled;
    private boolean personaLearningEnabled;
    private String personaLearnAt;
    private boolean personaEvalEnabled;
    private boolean originalPostEnabled;
    private int originalPostDailyCap;
    private String personaLastStatus;
    private Integer personaLastNewCount;
    private String personaLastLearnedAt;
    private String personaSummary;
    private Double mimicryAvg28d;
    private Integer mimicrySampleCount;
    private Double deleteRate28d;
    private Boolean gatePassed;

    /** Optional 28-day mimicry metrics from shadow eval (null until eval is wired). */
    public record MimicryMetrics(
        Double mimicryAvg28d,
        Integer mimicrySampleCount,
        Double deleteRate28d,
        Boolean gatePassed
    ) {}

    public static MarketingXOpsSettingsResponse from(MarketingXOpsSettingsService.XOpsSettings s) {
        return from(s, null, null);
    }

    public static MarketingXOpsSettingsResponse from(
            MarketingXOpsSettingsService.XOpsSettings s,
            XPersonaLearnService.LearnResult learn) {
        return from(s, learn, null);
    }

    public static MarketingXOpsSettingsResponse from(
            MarketingXOpsSettingsService.XOpsSettings s,
            XPersonaLearnService.LearnResult learn,
            MimicryMetrics metrics) {
        MarketingXOpsSettingsResponse.MarketingXOpsSettingsResponseBuilder b = MarketingXOpsSettingsResponse.builder()
            .morningTime(s.morningTime())
            .nightTime(s.nightTime())
            .storyScoopsPerDay(s.storyScoopsPerDay())
            .outboundDailyCap(s.outboundDailyCap())
            .inboundDailyCap(s.inboundDailyCap())
            .inboundPerPostCap(s.inboundPerPostCap())
            .hotMinReplies(s.hotMinReplies())
            .hotMaxAgeHours(s.hotMaxAgeHours())
            .ritualEnabled(s.ritualEnabled())
            .inboundEnabled(s.inboundEnabled())
            .outboundEnabled(s.outboundEnabled())
            .personaLearningEnabled(s.personaLearningEnabled())
            .personaLearnAt(s.personaLearnAt())
            .personaEvalEnabled(s.personaEvalEnabled())
            .originalPostEnabled(s.originalPostEnabled())
            .originalPostDailyCap(s.originalPostDailyCap());
        if (learn != null) {
            b.personaLastStatus(learn.status())
                .personaLastNewCount(learn.newManuals())
                .personaLastLearnedAt(learn.learnedAt() != null ? learn.learnedAt().toString() : null)
                .personaSummary(learn.summary());
        }
        if (metrics != null) {
            b.mimicryAvg28d(metrics.mimicryAvg28d())
                .mimicrySampleCount(metrics.mimicrySampleCount())
                .deleteRate28d(metrics.deleteRate28d())
                .gatePassed(metrics.gatePassed());
        }
        return b.build();
    }
}
