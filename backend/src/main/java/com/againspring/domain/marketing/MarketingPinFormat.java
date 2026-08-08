package com.againspring.domain.marketing;

/**
 * Pin format for {@link MarketingHoldingStatus#PINNED} (S3).
 * Maps 1:1 to {@code com.againspring.marketing.MarketingPublishFormat}
 * ({@code fromPin}) for S4 {@code resolveTargets}.
 */
public enum MarketingPinFormat {
    VIDEO,
    TEXT
}
