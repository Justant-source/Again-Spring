package com.againspring.aiuser.orchestrator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai-user")
public class OrchestratorProperties {
    private boolean enabled = false;
    private String tickCron = "0 */10 * * * *";
    private int dailyGlobalCap = 200;
    private String botPassword = "ai-user-dev-pw-2026";
    private String backendBaseUrl = "http://againspring-backend-dev:8080";
    private String llmAiUserUrl = "http://againspring-llm-ai-user:8092";
    private int personaTarget = 10;
    private String personasDir = "/app/personas";
    private boolean forceActive = false;  // 시간대 무관 강제 활성 (dev 테스트용)
    /** 보조 백엔드 URL (prod↔dev 동시 게시). 빈 문자열이면 미사용. */
    private String secondaryBackendBaseUrl = "";

    /** 콘텐츠 인식 좋아요·투표 결정 설정. */
    private ContentAwareDecisions contentAwareDecisions = new ContentAwareDecisions();
    /** paired post 비율/분포 설정. */
    private PairedPost pairedPost = new PairedPost();
    /** Plan-first foundation settings. Publisher/generator wiring is intentionally separate. */
    private ThreadPlan threadPlan = new ThreadPlan();
    /**
     * Human-reply batch timing and backlog TTL (§2.9 / Wave1-I).
     * Destructive TTL cleanup stays off until an admin trigger or explicit flag flip.
     */
    private HumanReply humanReply = new HumanReply();

    public boolean isContentAwareEnabled() {
        return contentAwareDecisions.isEnabled();
    }

    public int getAnalysisBudgetPerTick() {
        return contentAwareDecisions.getAnalysisBudgetPerTick();
    }

    @Getter
    @Setter
    public static class PairedPost {
        private boolean enabled = true;
        private String cron = "0 0 5 * * *";
        /** 한 번의 스케줄 실행에서 생성할 수 있는 최대 pair 수. */
        private int pairsPerRun = 2;
        /** 하루 전체 synthetic post 중 paired post가 최소로 차지해야 하는 비율. */
        private double targetShare = 0.15;
        /** paired post 내부에서 COUPLE/MARRIAGE가 차지해야 하는 비율. */
        private double romanticShare = 0.80;
    }

    @Getter
    @Setter
    public static class ContentAwareDecisions {
        /** 콘텐츠 인식 결정 on/off. off면 기존 affinity/bias 동작. */
        private boolean enabled = true;
        /** 틱당 신규 글 분석 LLM 호출 상한 (토큰 통제). */
        private int analysisBudgetPerTick = 3;
    }

    @Getter
    @Setter
    public static class HumanReply {
        /** Publish delay after batch success — inclusive minutes. */
        private int delayMinutesMin = 1;
        private int delayMinutesMax = 30;
        /** Inbox rows older than this (by observed_at / detected_at) → CANCELLED + EXPIRED_TTL. */
        private int inboxTtlDays = 7;
        /** REQUESTED ai_thread_plans older than this (by created_at) → EXPIRED + EXPIRED_TTL. */
        private int planTtlDays = 7;
        /**
         * When false (default), scheduled/auto TTL wipe is a no-op.
         * Admin {@code /admin/trigger/human-reply-ttl-cleanup} may still run with force=true.
         */
        private boolean ttlCleanupEnabled = false;
    }

    @Getter
    @Setter
    public static class ThreadPlan {
        /** Safe maintenance is opt-in until PLAN mode replaces the legacy executor. */
        private boolean maintenanceEnabled = false;
        /** PLAN rollout gate. Disabled by default so deployment cannot create content unexpectedly. */
        private boolean enabled = false;
        private boolean publisherEnabled = false;
        private boolean humanReplyBatchEnabled = false;
        /** Publishes posts held in ai_scheduled_posts once their slot arrives (2026-07-31~). */
        private boolean scheduledPostPublisherEnabled = false;
        private String scheduledPostPublisherCron = "0 * * * * *";
        private int scheduledPostPublishBatchSize = 5;
        /** Minimum spacing enforced between nightly-batch post publish slots. */
        private long postSlotMinSpacingMinutes = 45;
        private String aiPostProvider = "CODEX";
        private String humanPlanProvider = "CODEX";
        private String aiPostModel = "";
        private String humanPlanModel = "";
        private int publishBatchSize = 20;
        private int humanReplyMaxPosts = 10;
        private int humanReplyMaxComments = 50;
        /** 글+최대 24개 후보를 한 번에 요청하는 구조화 생성용 타임아웃 (단건 생성보다 오래 걸림). */
        private long bundleTimeoutMs = 240000;
        /**
         * When true (default), AI_POST bundle generation splits comment personas into
         * 4~6-sized micro-batches inside the initial job (no publish-time LLM).
         * When false, keeps the legacy single mega-call with the full cast.
         */
        private boolean microBatchEnabled = true;
        /** Personas per micro-batch (clamped to 4..6 at use via {@link #resolvedMicroBatchSize()}). */
        private int microBatchSize = 5;
        /**
         * After {@code ThreadQualityGate}, plan is READY only if kept top-level ≥ this
         * and kept items ≥ {@link #readyMinItems}. Below → {@code QUALITY_BELOW_MIN_ITEMS}.
         */
        private int readyMinTopLevel = 3;
        private int readyMinItems = 6;
        /** Max share of any single stance among stance-bearing kept items (WP4 hard invariant). */
        private double stanceShareMax = 0.80;
        private Map<Integer, Double> kstHourlyHumanWeights = defaultKstWeights();
        /** Engagement (likes/views) reconciler settings. */
        private Engagement engagement = new Engagement();

        /** Effective micro-batch size clamped to the plan contract (4..6). */
        public int resolvedMicroBatchSize() {
            int size = microBatchSize <= 0 ? 5 : microBatchSize;
            return Math.max(4, Math.min(6, size));
        }

        /**
         * 20~40대 커뮤니티 체류 패턴 근사치(2026-07-31 결정, 실측 데이터 아님 — 출퇴근 소피크·
         * 점심 소피크·저녁~심야 본피크(22시)·새벽 저활동 구조만 반영). {@link ActivityCurve}가
         * 새 글/댓글 발행 시각 샘플링에, {@link EffectiveExposureCalculator}가 노출시간 가중에 쓴다.
         */
        private static Map<Integer, Double> defaultKstWeights() {
            double[] hourly = {
                0.45, 0.30, 0.15, 0.08, 0.05, 0.05, 0.10, 0.25, 0.40, 0.45, 0.50, 0.50,
                0.65, 0.60, 0.50, 0.50, 0.50, 0.55, 0.65, 0.75, 0.85, 0.95, 1.00, 0.75,
            };
            Map<Integer, Double> weights = new LinkedHashMap<>();
            for (int hour = 0; hour < 24; hour++) weights.put(hour, hourly[hour]);
            return weights;
        }
    }

    @Getter
    @Setter
    public static class Engagement {
        private boolean enabled = false;
        private int lookbackDays = 3;
        private int maxPostsPerRun = 40;
        private int maxLikeCallsPerRun = 500;
        private boolean viewsEnabled = true;
        private double postLikePerView = 0.02;
        private double postLikePerComment = 0.6;
        private double commentLikePerView = 0.75;
        private double commentLikePerReply = 1.0;
        private int commentLikeCap = 12;
        private double replyLikePerView = 0.40;
        private int replyLikeCap = 5;
        /**
         * 한 실행에서 좋아요·투표 후보로 쓸 페르소나 풀 크기 (warm 우선 + cold 예산).
         * 2026-07-31 30→60: 투표는 votes.UNIQUE(post_id,voter_user_id) 제약 때문에 한
         * 페르소나가 한 글에 1표뿐이라, 댓글 좋아요(cap 12)와 달리 풀(30)보다 큰 투표
         * 타깃(최대 voteCap=80, 실무 도달치 최대 ~37)에 쉽게 걸린다. warm 우선 선발이라
         * 풀이 작으면 매 실행 거의 같은 인원이 뽑히고, 그 인원이 1회차에 전부 투표하면
         * 2회차부터 후보가 cold 예산만큼만 남는다.
         */
        private int personaPoolSize = 60;
        /** 풀 구성 시 새로 로그인(cold)시킬 페르소나 상한 — 분당 5회 로그인 레이트리밋 공유 대비. */
        private int coldLoginBudget = 3;

        /** 투표 리콘실 on/off (2026-07-31~, VoteLikeBatchService 대체). */
        private boolean votesEnabled = true;
        /**
         * 투표는 익명이라 실사용자가 댓글보다 훨씬 활발히 참여한다 — 조회수의 15%를
         * 타깃으로 잡는다(댓글 타깃보다 훨씬 큰 값, 사용자 확정 사양).
         */
        private double votePerView = 0.15;
        private int voteCap = 80;
        /** 댓글/대댓글 좋아요와 예산을 공유하지 않는 투표 전용 실행당 상한. */
        private int maxVoteCallsPerRun = 40;
        /** 한 글이 페르소나 풀을 통째로 소진해 나머지 글의 투표를 굶기는 걸 막는 상한. */
        private int maxVotesPerPostPerRun = 8;
        /** 글별 결정적 목표 A(작성자측) 비율의 하한 — prod 실측 자연 분포 44~80%. */
        private double voteAShareMin = 0.44;
        private double voteAShareMax = 0.80;

        /** 글 좋아요 리콘실 on/off (2026-07-31~, VoteLikeBatchService 대체). */
        private boolean postLikesEnabled = true;
    }
}
