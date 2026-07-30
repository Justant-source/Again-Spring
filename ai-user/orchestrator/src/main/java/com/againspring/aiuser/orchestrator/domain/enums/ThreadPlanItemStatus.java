package com.againspring.aiuser.orchestrator.domain.enums;

/** State of one independently idempotent scheduled action in a thread plan. */
public enum ThreadPlanItemStatus {
    RESERVED, SCHEDULED, PROCESSING, POSTED, CANCELLED, FAILED, EXPIRED
}
