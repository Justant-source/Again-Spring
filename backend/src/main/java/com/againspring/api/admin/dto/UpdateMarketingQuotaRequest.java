package com.againspring.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Update marketing daily caps.
 *
 * <p>Prefer Phase 2 platform fields. Legacy {@code dailyTextCap}/{@code dailyVideoCap}
 * still accepted and distributed into platform caps.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMarketingQuotaRequest {

    /** @deprecated Prefer platform fields. */
    @Min(1)
    @Max(50)
    private Integer dailyTextCap;

    /** @deprecated Prefer platform fields. */
    @Min(0)
    @Max(50)
    private Integer dailyVideoCap;

    @Min(0)
    @Max(50)
    private Integer xThread;

    @Min(0)
    @Max(50)
    private Integer instagramFeed;

    @Min(0)
    @Max(50)
    private Integer instagramReels;

    @Min(0)
    @Max(50)
    private Integer youtubeShorts;

    public boolean hasPlatformCaps() {
        return xThread != null || instagramFeed != null
            || instagramReels != null || youtubeShorts != null;
    }

    public boolean hasLegacyCaps() {
        return dailyTextCap != null && dailyVideoCap != null;
    }
}
