package com.againspring.aiuser.orchestrator.domain.enums;

/** Lifecycle of a pre-generated post held in {@code ai_scheduled_posts} until its publish slot. */
public enum ScheduledPostStatus {
    SCHEDULED, PUBLISHING, PUBLISHED, FAILED, CANCELLED
}
