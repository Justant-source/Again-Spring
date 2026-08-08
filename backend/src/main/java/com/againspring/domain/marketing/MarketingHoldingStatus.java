package com.againspring.domain.marketing;

/**
 * Waiting-board lifecycle for a post's marketing draft.
 * {@link #PINNED} is reserved for S3 soft-reserve; allowed in schema/enum now.
 */
public enum MarketingHoldingStatus {
    IN_POOL,
    PINNED,
    OUT_OF_CUT,
    COMMITTED,
    DROPPED
}
