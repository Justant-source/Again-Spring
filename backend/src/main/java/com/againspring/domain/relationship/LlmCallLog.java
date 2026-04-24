package com.againspring.domain.relationship;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * LLM 호출 로그 엔티티 (MariaDB JPA)
 * 모든 LLM 호출의 성능 및 메타데이터 기록
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "llm_call_logs",
    indexes = {
        @Index(columnList = "correlation_id", name = "idx_correlation_id"),
        @Index(columnList = "session_id", name = "idx_session_id")
    }
)
public class LlmCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, name = "correlation_id")
    private String correlationId;

    @Column(length = 50, name = "provider")
    private String provider;

    @Column(length = 32, name = "session_id")
    private String sessionId;

    @Column(name = "turn_number")
    private Integer turnNumber;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "input_length")
    private Integer inputLength;

    @Column(name = "output_length")
    private Integer outputLength;

    @Column(length = 32, name = "outcome")
    private String outcome; // success / fallback / timeout / error

    @Column(length = 64, name = "error_code")
    private String errorCode;

    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
