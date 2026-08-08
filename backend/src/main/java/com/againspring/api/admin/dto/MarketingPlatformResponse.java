package com.againspring.api.admin.dto;

import com.againspring.marketing.MarketingPlatformAutoService;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MarketingPlatformResponse {

    private String platform;
    private boolean autoEnabled;
    private boolean runtimeSupported;
    private String warning;

    public static MarketingPlatformResponse from(MarketingPlatformAutoService.PlatformStatus status) {
        return MarketingPlatformResponse.builder()
            .platform(status.platform())
            .autoEnabled(status.autoEnabled())
            .runtimeSupported(status.runtimeSupported())
            .warning(status.warning())
            .build();
    }
}
