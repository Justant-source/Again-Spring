package com.againspring.aiuser.orchestrator.domain.enums;

/** Inbox state for a human-authored comment that may receive one batched AI response. */
public enum HumanInteractionStatus {
    PENDING, PROCESSING, RESPONDED, SKIPPED, EXPIRED, FAILED
}
