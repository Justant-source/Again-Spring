package com.againspring.api.admin.dto;

import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.marketing.MarketingPublishFormat;
import com.againspring.marketing.holding.MarketingHoldingCommitService.ForceResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForceMarketingCompletedResponse {

    private String postId;
    private MarketingHoldingStatus status;
    private MarketingPublishFormat format;
    private List<Long> jobIds;
    private List<String> targets;

    public static ForceMarketingCompletedResponse from(ForceResult r) {
        return ForceMarketingCompletedResponse.builder()
            .postId(r.postId())
            .status(r.status())
            .format(r.format())
            .jobIds(r.jobIds())
            .targets(r.targets())
            .build();
    }
}
