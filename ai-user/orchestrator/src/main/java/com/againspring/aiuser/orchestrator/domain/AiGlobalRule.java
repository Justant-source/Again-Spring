package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AI 유저 공통 전역 금지 규칙 (읽기 전용).
 * backend V68 마이그레이션으로 생성된 ai_global_rules 테이블을 공유 스키마에서 읽는다.
 * orchestrator는 INSERT/UPDATE 하지 않는다 — 관리는 backend AdminAiRulesController가 담당.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_global_rules")
public class AiGlobalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_text", nullable = false, length = 500)
    private String ruleText;

    /** 'POST' | 'COMMENT' | 'ALL' */
    @Column(name = "scope", nullable = false, length = 16)
    private String scope;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
