-- V119: Create marketing_generation_trace table for LLM + render analysis
-- Date: 2026-08-27
-- Purpose: Audit log of marketing content generation — LLM prompts, raw responses,
--          plan artifacts, render diagnostics. One row per generation event.
--          No FK to marketing_job: trace is independent (job_id nullable, postId required).
--          Columns sfx_applied, sfx_skipped, sibom_applied, bgm_file, render_diag are
--          reserved for WaggleBot render feedback (Phase 2); Phase 1 code does not populate.

CREATE TABLE marketing_generation_trace (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_id        BIGINT NULL COMMENT 'Nullable FK to marketing_job; trace persists independent of job lifecycle',
  post_id       VARCHAR(32) NOT NULL COMMENT 'FK to posts (required); identifies which post this trace belongs to',
  platform      VARCHAR(40) NOT NULL COMMENT 'e.g. promo_title, x_thread, instagram_feed, sibom_plan, sibom_candidate, video_variant',
  stage         VARCHAR(32) NOT NULL COMMENT 'Generation stage: PROMO_TITLE, PLAN_GENERATION, GUARD_CHECK, VIDEO_RENDER, etc.',
  render_profile VARCHAR(32) NULL COMMENT 'Render profile used (marketing_fast | marketing_v2); NULL = not applicable',

  llm_model     VARCHAR(64) NULL COMMENT 'Model name (claude-haiku-4-5-20251001, claude-sonnet-5, etc.)',
  llm_prompt    LONGTEXT NULL COMMENT 'Full LLM input prompt (sanitized, no secrets)',
  llm_response  LONGTEXT NULL COMMENT 'Raw LLM output (before parsing/sanitization)',
  llm_attempt   INT NULL COMMENT 'Attempt number (1=initial, 2+=retry)',
  llm_result    VARCHAR(32) NULL COMMENT 'Result status: OK, PARSE_ERROR, LLM_ERROR, LLM_DISABLED, REFUSAL, TIMEOUT',
  llm_duration_ms BIGINT NULL COMMENT 'LLM call latency in milliseconds',

  final_hook    TEXT NULL COMMENT 'Finalized hook text (post-parsing, normalized)',
  final_script  LONGTEXT NULL COMMENT 'Finalized script (post-LLM, post-variant)',

  sibom_scores      JSON NULL COMMENT 'Sibom character candidacy scores: {character_name: score, ...}',
  sibom_plan_llm    JSON NULL COMMENT 'Sibom plan from LLM (raw, before guard check)',
  sibom_plan_final  JSON NULL COMMENT 'Sibom plan after guard/validation (finalized animation spec)',
  sibom_guard_log   JSON NULL COMMENT 'SibomPlanGuard decision log: {step, passed, reason}',

  sfx_applied   JSON NULL COMMENT '[Phase 2: WaggleBot] SFX applied to render: {name, duration_ms}[]',
  sfx_skipped   JSON NULL COMMENT '[Phase 2: WaggleBot] SFX skipped due to constraints: {name, reason}[]',
  sibom_applied JSON NULL COMMENT '[Phase 2: WaggleBot] Final Sibom rig state and frame indices applied',
  bgm_file      VARCHAR(255) NULL COMMENT '[Phase 2: WaggleBot] BGM filename selected',
  render_diag   JSON NULL COMMENT '[Phase 2: WaggleBot] Render diagnostics: frames, duration, errors',

  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  INDEX idx_mgt_job (job_id),
  INDEX idx_mgt_post (post_id),
  INDEX idx_mgt_platform_created (platform, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
