package com.againspring.api.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Update score weights. Prefer {@code platforms} (Phase 2). Legacy flat fields still accepted.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMarketingScoreWeightsRequest {

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double weightViews;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double weightComments;

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double weightVotes;

    /** platform id → {hook, vote_skew, comments, votes, views, has_partner}. */
    private Map<String, Map<String, Double>> platforms;

    /** Phase 2.7 — optional toggle; null = leave unchanged. */
    private Boolean autoAdjust;

    public boolean hasPlatformWeights() {
        return platforms != null && !platforms.isEmpty();
    }

    public boolean hasLegacyWeights() {
        return weightViews != null && weightComments != null && weightVotes != null;
    }

    public boolean hasAutoAdjust() {
        return autoAdjust != null;
    }
}
