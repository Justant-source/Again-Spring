package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "persona_history_entries",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_history_dedupe",
            columnNames = {"persona_id", "entry_type", "created_at", "target_post_id", "content_hash"}
        )
    }
)
public class PersonaHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "persona_id", length = 32, nullable = false)
    private String personaId;

    @Column(name = "entry_type", length = 16, nullable = false)
    private String entryType;

    @Column(name = "target_post_id", length = 32, nullable = false)
    @Builder.Default
    private String targetPostId = "";

    @Column(length = 32, nullable = false)
    @Builder.Default
    private String category = "";

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** example_bank.id provenance (loose, no hard FK) — WP2 V9 */
    @Column(name = "source_example_id")
    private Long sourceExampleId;

    /** ai_thread_plans.id provenance (loose, no hard FK) — WP2 V9 */
    @Column(name = "plan_id", length = 36)
    private String planId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
