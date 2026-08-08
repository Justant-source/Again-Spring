package com.againspring.api.admin.dto;

import com.againspring.domain.marketing.MarketingPinFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinMarketingHoldingRequest {

    @NotNull
    private MarketingPinFormat format;
}
