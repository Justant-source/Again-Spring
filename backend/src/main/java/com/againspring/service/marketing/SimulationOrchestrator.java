package com.againspring.service.marketing;

import com.againspring.domain.Report;
import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.ReportStatus;
import com.againspring.domain.enums.RelationType;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.domain.marketing.MarketingUsageLog;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.marketing.MarketingSimulationRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import com.againspring.repository.marketing.MarketingUsageLogRepository;
import com.againspring.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 마케팅 시뮬레이션 오케스트레이터 (V15.8 리팩토링).
 * 가상 사용자 A ↔ AI 중재자의 실제 Solo 채팅 플로우를 생성.
 * VirtualUserGenerator로 A 발화 생성 → ChatService.sendUserMessage로 중재자 응답 획득 →
 * requestFinalization으로 Sonnet 리포트 트리거 → 리포트 완료 대기.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SimulationOrchestrator {

    private final MarketingSimulationRepository simRepo;
    private final MarketingUsageLogRepository usageLogRepo;
    private final MarketingSourceStoryRepository storyRepo;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;
    private final ChatService chatService;
    private final PersonaInferenceService personaService;
    private final SimulationTurnPlanner turnPlanner;
    private final VirtualUserGenerator virtualUserGenerator;

    static final String MARKETING_SYSTEM_USER_ID = "marketing_system";

    private static final int MAX_DAILY_SIMULATIONS = 10;
    private static final BigDecimal MONTHLY_BUDGET_USD = BigDecimal.valueOf(20.0);
    private static final String MODEL_HAIKU = "claude-haiku-4-5-20251001";
    private static final int MAX_CRISIS_RETRIES = 2;
    private static final int REPORT_POLL_MAX_SECONDS = 300;

    // Haiku 가격 추정 (공식 기준: Input $0.80/1M, Output $4.00/1M tokens)
    private static final BigDecimal HAIKU_INPUT_PRICE = BigDecimal.valueOf(0.80)
            .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
    private static final BigDecimal HAIKU_OUTPUT_PRICE = BigDecimal.valueOf(4.00)
            .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
    // Sonnet 리포트 추정치 (실제 토큰 집계 불가, 플랫 $0.02 보수적 추정)
    private static final BigDecimal SONNET_REPORT_FLAT_COST = BigDecimal.valueOf(0.02);

    /**
     * 시뮬레이션 비동기 실행 (V15.8: 실제 Solo 채팅 플로우).
     * @Transactional 없음 — 각 서브 호출이 자체 트랜잭션을 관리.
     */
    @Async("marketingExecutor")
    public void runSimulation(Long simulationId) {
        String sessionId = null;
        try {
            log.info("Starting simulation {} (V15.8 real chat flow)", simulationId);

            // 1. 비용 한도 확인
            checkCostGuards();

            // 2. 시뮬레이션 및 스토리 로드
            MarketingSimulation simulation = simRepo.findById(simulationId)
                    .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));
            Long sourceStoryId = simulation.getSourceStoryId();
            MarketingSourceStory story = storyRepo.findById(sourceStoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Story not found: " + sourceStoryId));

            // 3. 상태 → RUNNING
            simulation.setStatus(MarketingSimulation.Status.RUNNING);
            simulation.setStartedAt(Instant.now());
            simRepo.save(simulation);

            // 4. 페르소나 추론 (A만 사용, B 제거)
            String personaJson = personaService.inferPersonas(story);
            simulation.setPersonaA(personaJson);
            simRepo.save(simulation);

            // 5. 마케팅 전용 Solo 세션 생성 (SessionService 우회 — 일일 한도·게스트 제한 적용 불가)
            Session session = buildMarketingSession(story);
            sessionRepository.save(session);
            sessionId = session.getId();

            simulation.setSessionId(sessionId);
            simRepo.save(simulation);

            // 6. 턴 루프: 가상 A 발화 → ChatService 중재자 응답
            List<String> conversationLog = new ArrayList<>();
            int cumulativeUserChars = 0;
            BigDecimal totalCost = BigDecimal.ZERO;
            int actualTurns = 0;
            String lastMediatorContent = null;

            for (int turn = 1; turnPlanner.shouldContinue(actualTurns, cumulativeUserChars, lastMediatorContent); turn++) {
                String aMessage = generateVirtualUserMessage(story, personaJson, conversationLog, turn);
                cumulativeUserChars += aMessage.length();

                // 위기 차단 시 재시도
                ChatService.ChatTurnResult result = null;
                for (int retry = 0; retry <= MAX_CRISIS_RETRIES; retry++) {
                    result = chatService.sendUserMessage(sessionId, MessageSender.USER_A, aMessage);
                    if (result.success()) break;
                    if (retry < MAX_CRISIS_RETRIES) {
                        log.warn("Crisis blocked in sim {} turn {}, retry {}/{}", simulationId, turn, retry + 1, MAX_CRISIS_RETRIES);
                        aMessage = virtualUserGenerator.getFallbackSafeMessage(turn);
                    }
                }

                if (result == null || !result.success()) {
                    throw new IllegalStateException("Virtual user message blocked by crisis detector after " + MAX_CRISIS_RETRIES + " retries");
                }

                conversationLog.add("A: " + aMessage);

                if (result.mediatorMsg() != null) {
                    lastMediatorContent = result.mediatorMsg().getContent();
                    conversationLog.add("Mediator: " + lastMediatorContent);
                }

                // 비용 추정 (4자 ≈ 1토큰)
                totalCost = totalCost.add(estimateCost(aMessage.length() / 4, aMessage.length() / 4));
                actualTurns++;
            }

            // 7. 리포트 트리거 (Sonnet Solo 리포트)
            chatService.requestFinalization(sessionId, MessageSender.USER_A);
            totalCost = totalCost.add(SONNET_REPORT_FLAT_COST);

            // 8. 리포트 완료 대기 (최대 REPORT_POLL_MAX_SECONDS)
            // null=timeout(대화 성공·리포트 백그라운드 진행중), true=OK, false=LLM hard-fail
            Boolean reportReady = pollForReport(sessionId);

            // 9. 시뮬레이션 완료
            // timeout은 COMPLETED — 대화 자체는 성공했고 리포트는 비동기로 계속 생성됨
            simulation = simRepo.findById(simulationId).orElseThrow();
            boolean hardFail = Boolean.FALSE.equals(reportReady);
            simulation.setStatus(hardFail ? MarketingSimulation.Status.FAILED : MarketingSimulation.Status.COMPLETED);
            if (hardFail) simulation.setErrorMessage("report-generation-failed");
            simulation.setActualTurnCount(actualTurns);
            simulation.setLlmCostUsd(totalCost);
            simulation.setFinishedAt(Instant.now());
            simulation.setConversationLog(String.join("\n", conversationLog));
            simRepo.save(simulation);

            // 10. 사용 로그 기록
            usageLogRepo.save(MarketingUsageLog.builder()
                    .simulationId(simulationId)
                    .model(MODEL_HAIKU)
                    .inputTokens(cumulativeUserChars / 4)
                    .outputTokens(cumulativeUserChars / 4)
                    .costUsd(totalCost)
                    .build());

            log.info("Simulation {} {}: {} turns, cost ${}", simulationId,
                    simulation.getStatus(), actualTurns, totalCost);

        } catch (IllegalStateException e) {
            log.warn("Simulation {} blocked: {}", simulationId, e.getMessage());
            updateSimulationFailed(simulationId, e.getMessage());
        } catch (Exception e) {
            log.error("Simulation {} failed", simulationId, e);
            updateSimulationFailed(simulationId, e.getMessage());
        }
    }

    private Session buildMarketingSession(MarketingSourceStory story) {
        String simSessionId = "ses_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Instant now = Instant.now();

        RelationType relationType = RelationType.FRIEND;
        if (story.getRelationType() != null) {
            try {
                relationType = RelationType.fromValue(story.getRelationType());
            } catch (Exception e) {
                log.warn("Unknown relationType '{}' for story {}, defaulting to FRIEND", story.getRelationType(), story.getId());
            }
        }

        return Session.builder()
                .id(simSessionId)
                .createdByUserId(MARKETING_SYSTEM_USER_ID)
                .relationType(relationType)
                .status(SessionStatus.CHATTING_SOLO)
                .soloMode(true)
                .testRun(true)
                .userAMessageCount(0)
                .userBMessageCount(0)
                .finalizeAgreedByA(false)
                .finalizeAgreedByB(false)
                .mediatorStyleX(50)
                .mediatorStyleY(50)
                .crisisDetections(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .contentExpiresAt(now.plus(30, ChronoUnit.DAYS))
                .build();
    }

    private String generateVirtualUserMessage(MarketingSourceStory story, String personaJson,
                                               List<String> recentLog, int turn) {
        return virtualUserGenerator.generateLine(story, personaJson, recentLog, turn);
    }

    /**
     * 리포트 완료 폴링.
     * @return true=OK, false=LLM hard-fail, null=timeout(대화 성공·리포트 진행 중)
     */
    private Boolean pollForReport(String sessionId) {
        for (int i = 0; i < REPORT_POLL_MAX_SECONDS; i++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            try {
                java.util.Optional<Report> report = reportRepository.findBySessionId(sessionId);
                if (report.isPresent()) {
                    ReportStatus status = report.get().getStatus();
                    if (status == ReportStatus.OK) return true;
                    if (status == ReportStatus.FAILED) {
                        log.warn("Report generation hard-failed for session {}", sessionId);
                        return false;
                    }
                }
            } catch (Exception e) {
                log.warn("Error polling report for session {}: {}", sessionId, e.getMessage());
            }
        }
        log.info("Report poll timeout after {}s for session {} — simulation marked COMPLETED, report still generating",
                REPORT_POLL_MAX_SECONDS, sessionId);
        return null;
    }

    private void checkCostGuards() {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant tomorrowStart = todayStart.plus(1, ChronoUnit.DAYS);

        long todayCount = usageLogRepo.countByCreatedAtBetween(todayStart, tomorrowStart);
        if (todayCount >= MAX_DAILY_SIMULATIONS) {
            throw new IllegalStateException("일일 시뮬레이션 한도(" + MAX_DAILY_SIMULATIONS + "건) 초과");
        }

        Instant monthStart = YearMonth.now(ZoneOffset.UTC)
                .atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextMonthStart = YearMonth.now(ZoneOffset.UTC)
                .plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal monthlySpend = usageLogRepo.sumCostByCreatedAtBetween(monthStart, nextMonthStart);
        if (monthlySpend.compareTo(MONTHLY_BUDGET_USD) >= 0) {
            throw new IllegalStateException("월 예산($" + MONTHLY_BUDGET_USD + ") 초과");
        }
    }

    private void updateSimulationFailed(Long simulationId, String errorMessage) {
        simRepo.findById(simulationId).ifPresent(sim -> {
            sim.setStatus(MarketingSimulation.Status.FAILED);
            sim.setErrorMessage(errorMessage);
            sim.setFinishedAt(Instant.now());
            simRepo.save(sim);
        });
    }

    private BigDecimal estimateCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens).multiply(HAIKU_INPUT_PRICE);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens).multiply(HAIKU_OUTPUT_PRICE);
        return inputCost.add(outputCost).setScale(4, RoundingMode.HALF_UP);
    }
}
