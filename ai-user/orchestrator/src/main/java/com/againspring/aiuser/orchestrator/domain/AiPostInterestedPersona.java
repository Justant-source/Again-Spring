package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Interested-persona pool for a post (W6-A). Seeded from plan cast at READY;
 * matcher/manual sources land later (W6-B+). Loose refs — no hard FK.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_post_interested_personas", uniqueConstraints = @UniqueConstraint(
        name = "uk_post_interested_persona", columnNames = {"post_id", "persona_id"}))
public class AiPostInterestedPersona {

    public static final String SOURCE_PLAN_CAST = "PLAN_CAST";
    public static final String SOURCE_MATCHER = "MATCHER";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", length = 32, nullable = false)
    private String postId;

    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(precision = 6, scale = 5)
    private BigDecimal score;

    @Column(length = 24, nullable = false)
    @Builder.Default
    private String source = SOURCE_PLAN_CAST;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (source == null || source.isBlank()) source = SOURCE_PLAN_CAST;
    }
}
