package com.againspring.aiuser.orchestrator.domain.enums;

/** Lifecycle of a revision-specific AI conversation plan. */
public enum ThreadPlanStatus {
    REQUESTED, GENERATING, READY, ACTIVE, COMPLETED, PAUSED, FAILED, CANCELLED, EXPIRED
}
