package com.againspring.domain.ai;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AI 첨삭 원천 기록.
 * 관리자가 AI 작성 글/댓글을 첨삭한 내역을 저장하며,
 * example_bank 환류 및 페르소나 voice_profile 주의사항 머지의 근거가 된다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_content_corrections")
public class AiContentCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 'POST' | 'COMMENT' */
    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    /** post.id(VARCHAR32) 또는 comment.id(BIGINT) 문자열화 */
    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    /** = users.id = personas.id */
    @Column(name = "persona_id", nullable = false, length = 32)
    private String personaId;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "original_text", nullable = false, columnDefinition = "LONGTEXT")
    private String originalText;

    @Column(name = "corrected_text", nullable = false, columnDefinition = "LONGTEXT")
    private String correctedText;

    /** 확정된 페르소나 주의사항(단문). 없으면 NULL. */
    @Column(name = "persona_caution", columnDefinition = "TEXT")
    private String personaCaution;

    @Column(name = "admin_id", nullable = false, length = 32)
    private String adminId;

    @Column(name = "applied_live", nullable = false)
    @Builder.Default
    private boolean appliedLive = false;

    @Column(name = "pushed_to_bank", nullable = false)
    @Builder.Default
    private boolean pushedToBank = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
