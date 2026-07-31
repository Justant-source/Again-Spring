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
    public static class ThreadPlan {
        /** Safe maintenance is opt-in until PLAN mode replaces the legacy executor. */
        private boolean maintenanceEnabled = false;
        /** PLAN rollout gate. Disabled by default so deployment cannot create content unexpectedly. */
        private boolean enabled = false;
        private boolean publisherEnabled = false;
        private boolean humanReplyBatchEnabled = false;
        private String aiPostProvider = "CODEX";
        private String humanPlanProvider = "CODEX";
        private String aiPostModel = "";
        private String humanPlanModel = "";
        private int publishBatchSize = 20;
        private int humanReplyMaxPosts = 10;
        private int humanReplyMaxComments = 50;
        /** 글+최대 24개 후보를 한 번에 요청하는 구조화 생성용 타임아웃 (단건 생성보다 오래 걸림). */
        private long bundleTimeoutMs = 240000;
        private Map<Integer, Double> kstHourlyHumanWeights = defaultKstWeights();

        private static Map<Integer, Double> defaultKstWeights() {
            Map<Integer, Double> weights = new LinkedHashMap<>();
            for (int hour = 0; hour < 24; hour++) weights.put(hour, 1.0d);
            return weights;
        }
    }
}
