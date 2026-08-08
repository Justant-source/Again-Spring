package com.againspring.api.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMarketingScoreWeightsRequest {

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double weightViews;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double weightComments;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private Double weightVotes;
}
