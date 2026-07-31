package com.againspring.aiuser.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * AI 유저 생성 정책 싱글톤 (backend V70 소유 테이블의 읽기 전용 매핑).
 * orchestrator가 DailyPlanner / ActionExecutor에서 읽어 backend 라우팅에 사용.
 * Flyway 마이그레이션 없음 — backend가 소유, orchestrator는 읽기만.
 */
@Getter
@Entity
@Table(name = "ai_user_generation_config")
@org.hibernate.annotations.Immutable
public class AiUserGenerationConfig {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "target_posts")    private int targetPosts;
    @Column(name = "target_comments") private int targetComments;
    @Column(name = "target_replies")  private int targetReplies;
    @Column(name = "target_votes")    private int targetVotes;
    @Column(name = "target_likes")    private int targetLikes;

    @Column(name = "auto_comment") private boolean autoComment;
    @Column(name = "auto_reply")   private boolean autoReply;

    @Column(name = "updated_by")  private String updatedBy;
    @Column(name = "updated_at")  private Instant updatedAt;

    @Column(name = "provider_ai_post_bundle") private String providerAiPostBundle;
    @Column(name = "provider_human_post_plan") private String providerHumanPostPlan;
    @Column(name = "provider_human_interaction") private String providerHumanInteraction;
    @Column(name = "provider_vote_like") private String providerVoteLike;
    @Column(name = "schedule_execution_paused") private boolean scheduleExecutionPaused;
    @Column(name = "ai_user_kill_switch") private boolean aiUserKillSwitch;
    @Column(name = "candidate_pool_size") private int candidatePoolSize;
    @Column(name = "human_batch_max_posts") private int humanBatchMaxPosts;
    @Column(name = "human_batch_max_interactions") private int humanBatchMaxInteractions;
}
