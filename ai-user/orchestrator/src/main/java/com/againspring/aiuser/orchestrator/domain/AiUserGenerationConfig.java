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

    // 댓글 생성량 설정 — SSOT는 /admin/ai-user. 0이면 미설정으로 보고 properties 기본값을 쓴다.
    @Column(name = "hr_responders_per_interaction_max") private int hrRespondersPerInteractionMax;
    @Column(name = "hr_distinct_personas_max")         private int hrDistinctPersonasMax;
    @Column(name = "hr_replies_per_persona_max")       private int hrRepliesPerPersonaMax;
    @Column(name = "hr_candidate_responders_max")      private int hrCandidateRespondersMax;
    @Column(name = "hr_chunk_size")                    private int hrChunkSize;
    @Column(name = "hr_delay_minutes_min")             private int hrDelayMinutesMin;
    @Column(name = "hr_delay_minutes_max")             private int hrDelayMinutesMax;

    @Column(name = "bundle_timeout_ms")                 private int bundleTimeoutMs;
    @Column(name = "nightly_paired_share")              private double nightlyPairedShare;
    @Column(name = "nightly_slot_from_hour")            private int nightlySlotFromHour;
    @Column(name = "nightly_slot_to_hour")              private int nightlySlotToHour;
    @Column(name = "nightly_slot_min_spacing_minutes")  private int nightlySlotMinSpacingMinutes;

    /** 대화 총상한은 저장하지 않는다 — distinct × perPersona 파생값. */

    /** DB 값 우선, 미설정/비정상이면 fallbackMs (보통 env bundleTimeoutMs). */
    public long resolveBundleTimeoutMs(long fallbackMs) {
        if (bundleTimeoutMs >= 60_000 && bundleTimeoutMs <= 900_000) return bundleTimeoutMs;
        return fallbackMs > 0 ? fallbackMs : 600_000L;
    }
    public int hrRepliesPerPostHumanMax() {
        return hrDistinctPersonasMax * hrRepliesPerPersonaMax;
    }
}
