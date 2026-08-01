package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Slim canonical/inferred fact (WP2 V10 — no validity/temporal columns).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "persona_fact_assertions",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_fact_persona_key",
        columnNames = {"persona_id", "fact_key"}
    )
)
public class PersonaFactAssertion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(name = "fact_key", length = 80, nullable = false)
    private String factKey;

    @Lob
    @Column(name = "fact_value", nullable = false, columnDefinition = "TEXT")
    private String factValue;

    /** EXPLICIT | INFERRED | SYNTHETIC_FILL | LEGACY_IMPORTED */
    @Column(length = 24, nullable = false)
    private String origin;

    @Column(nullable = false, precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal confidence = new BigDecimal("1.000");

    @Column(name = "evidence_ref", length = 255)
    private String evidenceRef;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private short schemaVersion = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
