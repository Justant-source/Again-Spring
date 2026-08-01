package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Semantic search capsule (≤3 per persona: INTEREST / EXPERIENCE / VALUE).
 * Embedding writes go through JdbcTemplate + {@code VEC_FromText} — see PersonaCapsuleService.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "persona_semantic_capsules",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_persona_capsule",
        columnNames = {"persona_id", "capsule_type", "topic_key"}
    )
)
public class PersonaSemanticCapsule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(name = "capsule_type", length = 24, nullable = false)
    private String capsuleType;

    @Column(name = "topic_key", length = 80, nullable = false)
    private String topicKey;

    @Lob
    @Column(name = "text_value", nullable = false, columnDefinition = "TEXT")
    private String textValue;

    /**
     * VECTOR(1024) is written only via JDBC {@code VEC_FromText} (PersonaCapsuleService).
     * Not mapped here — MariaDB VECTOR is awkward for Hibernate; presence implied by row + content_hash.
     */

    @Column(nullable = false, precision = 4, scale = 3)
    @Builder.Default
    private BigDecimal weight = new BigDecimal("1.000");

    @Column(length = 24, nullable = false)
    private String origin;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "evidence_ref", length = 255)
    private String evidenceRef;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private short schemaVersion = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
