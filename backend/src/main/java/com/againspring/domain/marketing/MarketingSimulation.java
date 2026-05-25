package com.againspring.domain.marketing;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 마케팅 시뮬레이션 엔티티 (MariaDB JPA)
 * 소스 스토리를 기반으로 한 AI 중재 시뮬레이션 실행 기록
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_simulations")
@EntityListeners(AuditingEntityListener.class)
public class MarketingSimulation {

    public enum Status {
        QUEUED, RUNNING, COMPLETED, FAILED, CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_story_id")
    private Long sourceStoryId;

    @Column(name = "session_id", unique = true, length = 32)
    private String sessionId;

    @Column(name = "persona_a", columnDefinition = "TEXT")
    private String personaA;

    @Column(name = "persona_b", columnDefinition = "TEXT")
    private String personaB;

    @Column(name = "turn_count", nullable = false)
    @Builder.Default
    private Integer turnCount = 8;

    @Column(name = "actual_turn_count")
    private Integer actualTurnCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.QUEUED;

    @Column(name = "conversation_log", columnDefinition = "MEDIUMTEXT")
    private String conversationLog;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "llm_cost_usd", precision = 8, scale = 4)
    private BigDecimal llmCostUsd;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
