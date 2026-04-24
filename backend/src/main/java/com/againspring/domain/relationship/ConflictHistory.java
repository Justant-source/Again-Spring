package com.againspring.domain.relationship;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 충돌 이력 엔티티 (MariaDB JPA)
 * 각 세션별 충돌 기록 (부드러운 쿼리/집계용)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "conflict_history",
    indexes = {
        @Index(columnList = "session_id", name = "idx_session_id"),
        @Index(columnList = "user_a_id, user_b_id", name = "idx_user_pair")
    }
)
public class ConflictHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32, nullable = false, name = "session_id")
    private String sessionId;

    @Column(length = 32, nullable = false, name = "user_a_id")
    private String userAId;

    @Column(length = 32, name = "user_b_id")
    private String userBId;

    @Column(length = 32, name = "relationship_type")
    private String relationshipType;

    @Column(length = 32, name = "conflict_type")
    private String conflictType;

    @Column(columnDefinition = "DECIMAL(3,1)", name = "temperature")
    private Double temperature;

    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
