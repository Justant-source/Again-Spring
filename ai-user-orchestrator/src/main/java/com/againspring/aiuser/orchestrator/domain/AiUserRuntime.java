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
@Table(name = "ai_user_runtime")
public class AiUserRuntime {

    @Id
    private Integer id;  // always 1 (singleton row)

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;  // master kill-switch

    @Column(name = "daily_global_cap", nullable = false)
    @Builder.Default
    private int dailyGlobalCap = 200;

    @Column(name = "actions_today", nullable = false)
    @Builder.Default
    private int actionsToday = 0;

    @Column(name = "day_bucket")
    private LocalDate dayBucket;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
