package com.againspring.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "daily_stats")
public class DailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate statDate;

    @Builder.Default
    private int dau = 0;

    @Builder.Default
    private int newUsers = 0;

    @Builder.Default
    private int guestSessions = 0;

    @Builder.Default
    private int memberSessions = 0;

    @Builder.Default
    private int completedSessions = 0;

    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal avgTurns = BigDecimal.ZERO;

    @Builder.Default
    private int crisisTriggers = 0;

    @Builder.Default
    private int feedbackCount = 0;

    @Builder.Default
    private int voteCount = 0;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> metadata;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
