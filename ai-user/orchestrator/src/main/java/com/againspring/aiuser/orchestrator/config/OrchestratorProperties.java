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
    /** prod|dev — {@link EnvironmentGuard}가 기동 시 검증. 기본값 없음(필수). */
    private String env = "";
    private boolean enabled = false;
    private String tickCron = "0 */10 * * * *";
    private int dailyGlobalCap = 200;
    private String botPassword = "ai-user-dev-pw-2026";
    /** backend 내부 API(/api/internal/ai-user) 인증 토큰. AI_USER_INTERNAL_TOKEN. */
    private String internalToken = "";
    private String backendBaseUrl = "";
    private String llmAiUserUrl = "http://againspring-llm-ai-user:8092";
    private String personasDir = "/app/personas";
    private boolean forceActive = false;  // 시간대 무관 강제 활성 (dev 테스트용)
    /** FAMILY 광장 AI 생성 활성화 (false = OTHER로 흡수). */
    private boolean familyPlazaGenerationEnabled = false;

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
        /** 하루 전체 synthetic post 중 paired post가 차지해야 하는 비율 (기본 20%). */
        private double targetShare = 0.20;
        /**
         * Partner answer delay Δ after author PUBLIC (minutes). Skewed sample in
         * [min, max] with median ≈ {@link #partnerDelayMedianMinutes} — see
         * {@link com.againspring.aiuser.orchestrator.service.threadplan.PartnerDelaySampler}.
         */
        private int partnerDelayMinutesMin = 10;
        private int partnerDelayMinutesMax = 120;
        private int partnerDelayMedianMinutes = 55;
        /** Publishes due rows in {@code ai_scheduled_partner_answers}. */
        private boolean partnerPublisherEnabled = true;
        private String partnerPublisherCron = "0 * * * * *";
        private int partnerPublishBatchSize = 5;
        /**
         * Author hold window for ActivityCurve sampling (KST hours, inclusive start /
         * exclusive-ish end via end-of-hour). Quiet 02–06 is hard-banned after sampling.
         */
        private int authorSlotFromHour = 8;
        private int authorSlotToHour = 23;
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
        /** Interactions per LLM call (§16.7 chunk_size). */
        private int chunkSize = 20;
        /** LLM may return 0..N replies per human comment (§16.7). */
        private int respondersPerInteractionMin = 0;
        private int respondersPerInteractionMax = 3;
        /** Distinct AI personas that may answer humans on one post. */
        private int distinctPersonasPerPostHumanMax = 3;
        /** Max human-reply items per persona per post. */
        private int repliesPerPersonaPerPostHumanMax = 5;
        /** Max human-reply items per post (3×5). */
        private int repliesPerPostHumanMax = 15;
        /** Candidate personas sent to the LLM per interaction (interested pool / degrade). */
        private int candidateRespondersMax = 8;
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
        private long bundleTimeoutMs = 600000;
        /** Structured-generation failure alerting via Telegram. */
        private StructuredGeneration structuredGeneration = new StructuredGeneration();
        /**
         * When true (default), AI_POST bundle generation splits comment personas into
         * 4~6-sized micro-batches inside the initial job (no publish-time LLM).
         * When false, keeps the legacy single mega-call with the full cast.
         */
        private boolean microBatchEnabled = true;
        /** Personas per micro-batch (clamped to 4..6 at use via {@link #resolvedMicroBatchSize()}). */
        private int microBatchSize = 5;
        /**
         * 2026-08-01: HUMAN_POST 반응 plan(ThreadPlanGenerationService)이 전체 활성 페르소나(150명)를
         * voice_profile 통째로 프롬프트에 넣다가 Claude 200K 토큰 한도를 넘겨 REQUESTED 백로그
         * 173건이 전부 FAILED로 소진됐다(실측 150명≈306K 토큰). 이 값은 한 번의 LLM 요청에
         * 후보로 넣는 페르소나 cast 상한이며, 매 호출마다 셔플해 뽑으므로 회전(WP1의 원래 의도)은
         * 유지된다. micro-batch(4~6명)로 이미 쪼개는 AI_POST 경로에는 영향 없음.
         */
        private int planPersonaCastMax = 40;
        /**
         * Target after {@code ThreadQualityGate}: kept top-level ≥ this and kept items ≥
         * {@link #readyMinItems}. Miss → one comment LLM regen; still miss → thin READY
         * with whatever kept (no {@code QUALITY_BELOW_MIN_ITEMS} discard).
         */
        private int readyMinTopLevel = 3;
        private int readyMinItems = 6;
        /** Max share of any single stance among stance-bearing kept items (WP4 hard invariant). */
        private double stanceShareMax = 0.80;
        private Map<Integer, Double> kstHourlyHumanWeights = defaultKstWeights();
        /** Engagement (likes/views) reconciler settings. */
        private Engagement engagement = new Engagement();
        /** Plaza topical-fit gate settings (Phase 4: log-only mode). */
        private PlazaTopicalFitGate plazaTopicalFitGate = new PlazaTopicalFitGate();

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
    public static class StructuredGeneration {
        /** Enable/disable structured-generation failure alerting via Telegram. */
        private boolean failureAlertsEnabled = true;
        /** Number of PARSE_FAIL events within window to trigger alert. */
        private int parseFailThreshold = 3;
        /** Time window in minutes for PARSE_FAIL counting. */
        private int parseFailWindowMinutes = 30;
        /** Cooldown in minutes after alert sent (suppresses duplicate alerts). */
        private int parseFailCooldownMinutes = 360;
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

    @Getter
    @Setter
    public static class PlazaTopicalFitGate {
        /**
         * Enable logging of plaza topical-fit verdicts (MATCH/MISMATCH).
         * When true (default), evaluates generated stories and logs verdicts.
         * When false, evaluation is skipped entirely.
         */
        private boolean loggingEnabled = true;
        /**
         * Enable blocking of mismatched stories (blocking = not publishing).
         * When true, MISMATCH verdicts prevent post publication.
         * When false (default), all verdicts are log-only (no blocking).
         */
        private boolean blockingEnabled = false;
    }
}
