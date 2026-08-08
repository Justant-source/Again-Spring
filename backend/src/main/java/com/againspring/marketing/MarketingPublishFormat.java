package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPinFormat;

/**
 * Format assigned when a holding row is committed (pin / auto / force).
 * Used by {@link MarketingPlatformAutoService#resolveTargets}.
 *
 * <p>Maps 1:1 from holding {@link MarketingPinFormat} (domain/JPA) — same VIDEO/TEXT
 * values; keep both so the entity stays free of publish-service coupling (S4 wiring).
 */
public enum MarketingPublishFormat {
    VIDEO,
    TEXT;

    /** S4: convert pinned holding format to publish target format. */
    public static MarketingPublishFormat fromPin(MarketingPinFormat pin) {
        if (pin == null) {
            throw new IllegalArgumentException("pin format is required");
        }
        return switch (pin) {
            case VIDEO -> VIDEO;
            case TEXT -> TEXT;
        };
    }
}
