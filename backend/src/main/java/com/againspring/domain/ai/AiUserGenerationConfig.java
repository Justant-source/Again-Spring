package com.againspring.domain.ai;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AI 유저 생성 정책 싱글톤 (§11 토큰 관제).
 * id=1 단일 행. /admin/ai-user 에서 관리자가 수정.
 * orchestrator DailyPlanner가 읽어 페르소나별 쿼터를 분배하고 CLI/API 라우팅에 사용.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_user_generation_config")
public class AiUserGenerationConfig {

    @Id
    @Column(name = "id")
    @Builder.Default
    private Integer id = 1;

    // ── 일일 목표량 ────────────────────────────────────────────────────────
    @Column(name = "target_posts",    nullable = false) @Builder.Default private int targetPosts    = 0;
    @Column(name = "target_comments", nullable = false) @Builder.Default private int targetComments = 0;
    @Column(name = "target_replies",  nullable = false) @Builder.Default private int targetReplies  = 0;
    @Column(name = "target_votes",    nullable = false) @Builder.Default private int targetVotes    = 0;
    @Column(name = "target_likes",    nullable = false) @Builder.Default private int targetLikes    = 0;

    // ── 자동 비율 연동 ────────────────────────────────────────────────────
    @Column(name = "auto_comment", nullable = false) @Builder.Default private boolean autoComment = true;
    @Column(name = "auto_reply",   nullable = false) @Builder.Default private boolean autoReply   = true;

    // ── 계획형 실행기 설정 (PLAN 모드 일원화) ────────────────────────────────
    @Column(name = "provider_ai_post_bundle", nullable = false, length = 16) @Builder.Default private String providerAiPostBundle = "OFF";
    @Column(name = "provider_human_post_plan", nullable = false, length = 16) @Builder.Default private String providerHumanPostPlan = "OFF";
    @Column(name = "provider_human_interaction", nullable = false, length = 16) @Builder.Default private String providerHumanInteraction = "OFF";
    @Column(name = "provider_vote_like", nullable = false, length = 16) @Builder.Default private String providerVoteLike = "OFF";
    @Column(name = "schedule_execution_paused", nullable = false) @Builder.Default private boolean scheduleExecutionPaused = false;
    @Column(name = "ai_user_kill_switch", nullable = false) @Builder.Default private boolean aiUserKillSwitch = false;
    @Column(name = "candidate_pool_size", nullable = false) @Builder.Default private int candidatePoolSize = 24;
    @Column(name = "human_batch_max_posts", nullable = false) @Builder.Default private int humanBatchMaxPosts = 10;
    @Column(name = "human_batch_max_interactions", nullable = false) @Builder.Default private int humanBatchMaxInteractions = 50;

    // ── 댓글 생성량 설정 (SSOT: /admin/ai-user) ──────────────────────────────
    // 총 상한(15)은 저장하지 않는다: distinct × perPersona 로 항상 파생 → 3×5≠15 상태가 불가능.
    @Column(name = "hr_responders_per_interaction_max", nullable = false) @Builder.Default private int hrRespondersPerInteractionMax = 3;
    @Column(name = "hr_distinct_personas_max", nullable = false)         @Builder.Default private int hrDistinctPersonasMax = 3;
    @Column(name = "hr_replies_per_persona_max", nullable = false)       @Builder.Default private int hrRepliesPerPersonaMax = 5;
    @Column(name = "hr_candidate_responders_max", nullable = false)      @Builder.Default private int hrCandidateRespondersMax = 8;
    @Column(name = "hr_chunk_size", nullable = false)                    @Builder.Default private int hrChunkSize = 20;
    @Column(name = "hr_delay_minutes_min", nullable = false)             @Builder.Default private int hrDelayMinutesMin = 1;
    @Column(name = "hr_delay_minutes_max", nullable = false)             @Builder.Default private int hrDelayMinutesMax = 30;

    // ── 생성 런타임 (타임아웃·새벽 배치) — 저장 즉시 반영 ───────────────────
    /** 구조화 LLM 호출 타임아웃(ms). solo/paired/human-reply. 60_000~900_000. */
    @Column(name = "bundle_timeout_ms", nullable = false) @Builder.Default private int bundleTimeoutMs = 600_000;
    /** 새벽 배치 양면 비율 (0~1). ceil(target_posts × share). */
    @Column(name = "nightly_paired_share", nullable = false) @Builder.Default private double nightlyPairedShare = 0.20;
    @Column(name = "nightly_slot_from_hour", nullable = false) @Builder.Default private int nightlySlotFromHour = 8;
    @Column(name = "nightly_slot_to_hour", nullable = false) @Builder.Default private int nightlySlotToHour = 22;
    @Column(name = "nightly_slot_min_spacing_minutes", nullable = false) @Builder.Default private int nightlySlotMinSpacingMinutes = 45;

    /** 한 사람×한 게시글 대화의 AI 답글 총상한. 저장값이 아니라 파생값이다. */
    @Transient
    public int getHrRepliesPerPostHumanMax() {
        return hrDistinctPersonasMax * hrRepliesPerPersonaMax;
    }

    // ── 메타 ──────────────────────────────────────────────────────────────
    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
