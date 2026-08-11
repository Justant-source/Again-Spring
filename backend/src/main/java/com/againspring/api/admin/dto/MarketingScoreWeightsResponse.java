package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingScoreWeightService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingScoreWeightsResponse {

    /** @deprecated Phase 1 flat board weights — still returned for FE compatibility. */
    private double weightViews;
    /** @deprecated Phase 1 flat board weights. */
    private double weightComments;
    /** @deprecated Phase 1 flat board weights. */
    private double weightVotes;

    /** Phase 2: platform → signal → weight. */
    private Map<String, Map<String, Double>> platforms;

    /** Phase 2.7 — when true, weekly job nudges platform weights from stats. Default false. */
    private boolean autoAdjust;

    public static MarketingScoreWeightsResponse from(MarketingScoreWeightService.Weights weights) {
        return MarketingScoreWeightsResponse.builder()
            .weightViews(weights.weightViews())
            .weightComments(weights.weightComments())
            .weightVotes(weights.weightVotes())
            .autoAdjust(false)
            .build();
    }

    public static MarketingScoreWeightsResponse fromPlatform(
            MarketingScoreWeightService service,
            MarketingScoreWeightService.AllPlatformWeights all) {
        MarketingScoreWeightService.Weights legacy = service.getWeights();
        return MarketingScoreWeightsResponse.builder()
            .weightViews(legacy.weightViews())
            .weightComments(legacy.weightComments())
            .weightVotes(legacy.weightVotes())
            .platforms(service.toNestedMap(all))
            .autoAdjust(service.isAutoAdjustEnabled())
            .build();
    }
}
