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

    // ── 메타 ──────────────────────────────────────────────────────────────
    @Column(name = "updated_by", length = 32)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
