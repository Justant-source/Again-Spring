package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "daily_planner_retry_log")
public class DailyPlannerRetryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_bucket", nullable = false)
    private LocalDate dayBucket;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 1;

    @Column(name = "status", length = 32, nullable = false)
    @Builder.Default
    private String status = "PENDING";  // PENDING, SUCCESS, FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "error_class", length = 255)
    private String errorClass;

    @Column(name = "stacktrace_excerpt", columnDefinition = "TEXT")
    private String stacktraceExcerpt;

    @Column(name = "previous_attempt_at")
    private Instant previousAttemptAt;

    @Column(name = "retry_attempted_at")
    private Instant retryAttemptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
