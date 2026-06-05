package com.againspring.domain.ai;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AI 유저 공통 금지 규칙.
 * 모든 AI 유저가 글/댓글 생성 시 반드시 피해야 할 규칙을 누적하며,
 * orchestrator → llm 프롬프트 주입으로 적용된다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_global_rules")
public class AiGlobalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "~하지 말 것" 형식 단문 가이드 */
    @Column(name = "rule_text", nullable = false, length = 500)
    private String ruleText;

    /** 'POST' | 'COMMENT' | 'ALL' */
    @Column(name = "scope", nullable = false, length = 16)
    @Builder.Default
    private String scope = "ALL";

    /** 유래 첨삭 ID. 수동 추가 시 NULL. */
    @Column(name = "source_correction_id")
    private Long sourceCorrectionId;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by", nullable = false, length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
