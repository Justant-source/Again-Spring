package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingXOpsSettingsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Full replace of X ops knobs. All fields required so the admin form cannot
 * accidentally clear a value by omitting it.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMarketingXOpsSettingsRequest {

    private String morningTime;
    private String nightTime;

    @Min(0)
    @Max(10)
    private Integer storyScoopsPerDay;

    @Min(0)
    @Max(100)
    private Integer outboundDailyCap;

    @Min(0)
    @Max(200)
    private Integer inboundDailyCap;

    @Min(0)
    @Max(50)
    private Integer inboundPerPostCap;

    @Min(0)
    @Max(50)
    private Integer hotMinReplies;

    @Min(1)
    @Max(48)
    private Integer hotMaxAgeHours;

    private Boolean ritualEnabled;
    private Boolean inboundEnabled;
    private Boolean outboundEnabled;
    private Boolean personaLearningEnabled;
    private String personaLearnAt;
    private Boolean personaEvalEnabled;
    private Boolean originalPostEnabled;

    @Min(0)
    @Max(5)
    private Integer originalPostDailyCap;

    public MarketingXOpsSettingsService.XOpsSettings toSettings(
            MarketingXOpsSettingsService.XOpsSettings current) {
        return new MarketingXOpsSettingsService.XOpsSettings(
            morningTime != null ? morningTime : current.morningTime(),
            nightTime != null ? nightTime : current.nightTime(),
            storyScoopsPerDay != null ? storyScoopsPerDay : current.storyScoopsPerDay(),
            outboundDailyCap != null ? outboundDailyCap : current.outboundDailyCap(),
            inboundDailyCap != null ? inboundDailyCap : current.inboundDailyCap(),
            inboundPerPostCap != null ? inboundPerPostCap : current.inboundPerPostCap(),
            hotMinReplies != null ? hotMinReplies : current.hotMinReplies(),
            hotMaxAgeHours != null ? hotMaxAgeHours : current.hotMaxAgeHours(),
            ritualEnabled != null ? ritualEnabled : current.ritualEnabled(),
            inboundEnabled != null ? inboundEnabled : current.inboundEnabled(),
            outboundEnabled != null ? outboundEnabled : current.outboundEnabled(),
            personaLearningEnabled != null ? personaLearningEnabled : current.personaLearningEnabled(),
            personaLearnAt != null ? personaLearnAt : current.personaLearnAt(),
            personaEvalEnabled != null ? personaEvalEnabled : current.personaEvalEnabled(),
            originalPostEnabled != null ? originalPostEnabled : current.originalPostEnabled(),
            originalPostDailyCap != null ? originalPostDailyCap : current.originalPostDailyCap()
        );
    }
}
