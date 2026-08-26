package com.againspring.domain.marketing;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Audit log of marketing content generation events — LLM prompts, responses, and render diagnostics.
 * One row per generation stage (promo_title, plan, sibom, variant, etc.).
 * No FK to marketing_job: trace persists independent of job lifecycle.
 * Columns sfx_applied, sfx_skipped, sibom_applied, bgm_file, render_diag are
 * reserved for WaggleBot render feedback (Phase 2 implementation).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "marketing_generation_trace")
@EntityListeners(AuditingEntityListener.class)
public class MarketingGenerationTrace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nullable FK to marketing_job; trace persists independent of job lifecycle. */
    @Column
    private Long jobId;

    /** FK to posts (required); identifies which post this trace belongs to. */
    @Column(nullable = false, length = 32)
    private String postId;

    /** Platform identifier (e.g., promo_title, x_thread, instagram_feed, sibom_plan, sibom_candidate, video_variant). */
    @Column(nullable = false, length = 40)
    private String platform;

    /** Generation stage name (e.g., PROMO_TITLE, PLAN_GENERATION, GUARD_CHECK, VIDEO_RENDER). */
    @Column(nullable = false, length = 32)
    private String stage;

    /** Render profile used (marketing_fast | marketing_v2); NULL = not applicable. */
    @Column(length = 32)
    private String renderProfile;

    /** Model name (e.g., claude-haiku-4-5-20251001, claude-sonnet-5). */
    @Column(length = 64)
    private String llmModel;

    /** Full LLM input prompt (sanitized, no secrets). */
    @Column(columnDefinition = "LONGTEXT")
    private String llmPrompt;

    /** Raw LLM output (before parsing/sanitization). */
    @Column(columnDefinition = "LONGTEXT")
    private String llmResponse;

    /** Attempt number (1=initial, 2+=retry). */
    @Column
    private Integer llmAttempt;

    /** Result status (OK, PARSE_ERROR, LLM_ERROR, LLM_DISABLED, REFUSAL, TIMEOUT). */
    @Column(length = 32)
    private String llmResult;

    /** LLM call latency in milliseconds. */
    @Column
    private Long llmDurationMs;

    /** Finalized hook text (post-parsing, normalized). */
    @Column(columnDefinition = "TEXT")
    private String finalHook;

    /** Finalized script (post-LLM, post-variant). */
    @Column(columnDefinition = "LONGTEXT")
    private String finalScript;

    /** Sibom character candidacy scores: {character_name: score, ...}. Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String sibomScores;

    /** Sibom plan from LLM (raw, before guard check). Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String sibomPlanLlm;

    /** Sibom plan after guard/validation (finalized animation spec). Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String sibomPlanFinal;

    /** SibomPlanGuard decision log: {step, passed, reason}. Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String sibomGuardLog;

    /** [Phase 2: WaggleBot] SFX applied to render: {name, duration_ms}[]. Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String sfxApplied;

    /** [Phase 2: WaggleBot] SFX skipped due to constraints: {name, reason}[]. Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String sfxSkipped;

    /** [Phase 2: WaggleBot] Final Sibom rig state and frame indices applied. Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String sibomApplied;

    /** [Phase 2: WaggleBot] BGM filename selected. */
    @Column(length = 255)
    private String bgmFile;

    /** [Phase 2: WaggleBot] Render diagnostics: frames, duration, errors. Stored as JSON string. */
    @Column(columnDefinition = "JSON")
    private String renderDiag;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
