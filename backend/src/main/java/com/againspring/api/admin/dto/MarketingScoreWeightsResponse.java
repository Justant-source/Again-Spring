package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingScoreWeightService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingScoreWeightsResponse {

    private double weightViews;
    private double weightComments;
    private double weightVotes;

    public static MarketingScoreWeightsResponse from(MarketingScoreWeightService.Weights weights) {
        return MarketingScoreWeightsResponse.builder()
            .weightViews(weights.weightViews())
            .weightComments(weights.weightComments())
            .weightVotes(weights.weightVotes())
            .build();
    }
}
