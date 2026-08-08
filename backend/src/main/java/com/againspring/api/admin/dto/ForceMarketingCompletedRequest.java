package com.againspring.api.admin.dto;

import com.againspring.marketing.holding.MarketingHoldingCommitService.ForceMode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForceMarketingCompletedRequest {

    /** {@code VIDEO_AND_TEXT} or {@code TEXT_ONLY} */
    @NotNull
    private ForceMode mode;
}
