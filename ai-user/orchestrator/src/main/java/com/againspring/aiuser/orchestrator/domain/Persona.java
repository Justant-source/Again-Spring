package com.againspring.aiuser.orchestrator.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * WP1 신원 축(계약 1, V22): age_years/gender/marital/married_years/has_kids/job_type/job_title/
 * style_axes/last_post_at/last_comment_at. 기존 voice_profile.age(밴드)/gender/job은 호환용으로
 * 동시 갱신한다(PersonaProfileRegenerator가 병합 규칙을 담당).
 */

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

    // ── WP1 신원 축 (V22, 계약 1) ────────────────────────────────────────────

    @Column(name = "age_years", nullable = false)
    @Builder.Default
    private int ageYears = 30;

    @Column(length = 1, nullable = false)
    @Builder.Default
    private String gender = "F";

    @Column(length = 16, nullable = false)
    @Builder.Default
    private String marital = "SINGLE";  // SINGLE/DATING/ENGAGED/MARRIED

    @Column(name = "married_years")
    private Integer marriedYears;  // MARRIED만. null 허용.

    @Column(name = "has_kids", nullable = false)
    @Builder.Default
    private boolean hasKids = false;

    @Column(name = "job_type", length = 24, nullable = false)
    @Builder.Default
    private String jobType = "CORP_LARGE";

    @Column(name = "job_title", length = 80)
    private String jobTitle;

    @Type(JsonType.class)
    @Column(name = "style_axes", columnDefinition = "JSON")
    private Map<String, String> styleAxes;

    @Column(name = "last_post_at")
    private Instant lastPostAt;

    @Column(name = "last_comment_at")
    private Instant lastCommentAt;
}
