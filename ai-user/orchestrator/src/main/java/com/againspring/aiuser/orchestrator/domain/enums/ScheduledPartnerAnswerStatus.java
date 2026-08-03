package com.againspring.aiuser.orchestrator.domain.enums;

/** Lifecycle of a delayed partner answer held until {@code scheduledPartnerAt}. */
public enum ScheduledPartnerAnswerStatus {
    SCHEDULED, PUBLISHING, COMPLETED, FAILED, CANCELLED
}
