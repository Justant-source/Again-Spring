package com.againspring.aiuser.orchestrator.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Minimal match audit row (WP2 V12 / plan §6.3). Written by search path (W3-C).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "persona_match_audits")
public class PersonaMatchAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_id", length = 80, nullable = false)
    private String correlationId;

    @Column(name = "source_example_id", nullable = false)
    private Long sourceExampleId;

    @Column(length = 24, nullable = false)
    private String purpose;

    @Column(name = "persona_id", length = 32)
    private String personaId;

    @Column(name = "hard_filter_passed", nullable = false)
    private boolean hardFilterPassed;

    @Column(name = "semantic_score", precision = 6, scale = 5)
    private BigDecimal semanticScore;

    @Column(name = "final_score", precision = 6, scale = 5)
    private BigDecimal finalScore;

    @Column(nullable = false)
    private boolean selected;

    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "JSON")
    private Map<String, Object> reasons;

    @Column(name = "random_seed")
    private Long randomSeed;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
