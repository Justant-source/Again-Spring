package com.againspring.domain.relationship;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 온도 이력 엔티티 (MariaDB JPA)
 * 사용자별 관계 온도 변화 추적
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "temperature_history",
    indexes = {
        @Index(columnList = "user_id", name = "idx_user_id")
    }
)
public class TemperatureHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 32, nullable = false, name = "user_id")
    private String userId;

    @Column(length = 32, name = "related_user_id")
    private String relatedUserId;

    @Column(length = 32, nullable = false, name = "session_id")
    private String sessionId;

    @Column(columnDefinition = "DECIMAL(3,1)", name = "temperature")
    private Double temperature;

    @Column(nullable = false, updatable = false, name = "recorded_at")
    private Instant recordedAt;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
