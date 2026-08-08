package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * LLM Generation Gate: 단일행 런타임 제어 엔티티.
 * LLM 세션 실패 시 GENERATION(생성)만 홀딩 — PUBLISHING(발행)은 계속됨 (generate != publish).
 *
 * <p>singleton row (id=1)로 관리. 상태:</p>
 * <ul>
 *   <li>ACTIVE: 생성 진행 (정상)</li>
 *   <li>HELD: 생성 차단 (LLM 장애)</li>
 * </ul>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llm_generation_gate")
public class LlmGenerationGate {

    @Id
    private Integer id;  // always 1 (singleton row)

    @Column(nullable = false)
    @Builder.Default
    private String state = "ACTIVE";  // ACTIVE | HELD

    @Column(name = "last_held_at")
    private Instant lastHeldAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;  // nullable, e.g. "LLM API rate limit exceeded"

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
