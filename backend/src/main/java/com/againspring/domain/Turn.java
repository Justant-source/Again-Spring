package com.againspring.domain;

import com.againspring.domain.enums.TurnRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 턴 엔티티 (MariaDB JPA)
 * Session과 분리된 독립 엔티티
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "turns")
public class Turn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(nullable = false)
    private Integer turnNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TurnRole role;

    @Column(length = 32)
    private String userId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String mediatorMessage;

    @Column(columnDefinition = "TEXT")
    private String mediatorSummaryForOpponent;

    @Column
    @Builder.Default
    private Boolean isPerspectiveTaking = false;

    @Column
    @Builder.Default
    private Boolean skipped = false;

    @Column
    @Builder.Default
    private Integer tokensUsed = 0;

    @Column
    @Builder.Default
    private Long llmLatencyMs = 0L;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
