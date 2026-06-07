package com.againspring.aiuser.orchestrator.domain;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 글 분석 캐시 — 글 1건당 LLM 1회 분석 결과. 좋아요·투표 결정의 콘텐츠 신호.
 * 게시 후 글은 불변이므로 영구 캐시. JSON 매핑은 {@link Persona} 패턴 차용.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "post_analysis")
public class PostAnalysis {

    @Id
    @Column(name = "post_id", length = 32)
    private String postId;

    /** 0=작성자 명백히 잘못, 0.5=반반, 1=작성자 명백한 피해자 */
    @Column(name = "author_sympathy", nullable = false, precision = 3, scale = 2)
    private BigDecimal authorSympathy;

    /** 양쪽 주장이 팽팽한 정도 (높을수록 50:50으로 수렴) */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal ambiguity;

    /** 갈등의 감정적 강도 */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal severity;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON", nullable = false)
    private List<String> topics;

    @Type(JsonType.class)
    @Column(columnDefinition = "JSON", nullable = false)
    private List<String> emotions;

    /** 인식된 archetype id (best-effort, 없으면 null) */
    @Column(name = "archetype_frame", length = 64)
    private String archetypeFrame;

    /** progressive|conservative|neutral */
    @Column(name = "political_hint", length = 16, nullable = false)
    @Builder.Default
    private String politicalHint = "neutral";

    @Column(name = "analyzed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant analyzedAt = Instant.now();

    @Column(name = "correlation_id", length = 64)
    private String correlationId;
}
