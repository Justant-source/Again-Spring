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
    private String personaLastStatus;
    private Integer personaLastNewCount;
    private String personaLastLearnedAt;
    private String personaSummary;

    public static MarketingXOpsSettingsResponse from(MarketingXOpsSettingsService.XOpsSettings s) {
        return MarketingXOpsSettingsResponse.builder()
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
            .build();
    }

    public static MarketingXOpsSettingsResponse from(
            MarketingXOpsSettingsService.XOpsSettings s,
            XPersonaLearnService.LearnResult learn) {
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
            .personaLearnAt(s.personaLearnAt());
        if (learn != null) {
            b.personaLastStatus(learn.status())
                .personaLastNewCount(learn.newManuals())
                .personaLastLearnedAt(learn.learnedAt() != null ? learn.learnedAt().toString() : null)
                .personaSummary(learn.summary());
        }
        return b.build();
    }
}
