package com.againspring.aiuser.orchestrator.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "personas")
public class Persona {

    @Id
    @Column(length = 32)
    private String id;  // = users.id

    @Column(length = 64, nullable = false)
    private String archetype;

    @Column(length = 16, nullable = false)
    private String tier;  // HEAVY/REGULAR/LIGHT/DORMANT as string

    @Type(JsonType.class)
    @Column(name = "voice_profile", columnDefinition = "JSON", nullable = false)
    private Map<String, Object> voiceProfile;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON", nullable = false)
    private Map<String, Double> interests;

    @Type(JsonType.class)
    @Column(name = "bias_profile", columnDefinition = "JSON", nullable = false)
    private Map<String, Double> biasProfile;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON", nullable = false)
    private List<Double> circadian;  // 24-bucket activity weights (KST hour 0-23)

    @Column(name = "slang_level", precision = 3, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal slangLevel = new BigDecimal("0.50");

    @Column(name = "daily_target", nullable = false)
    @Builder.Default
    private int dailyTarget = 6;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
