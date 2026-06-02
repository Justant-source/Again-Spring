package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.domain.marketing.MarketingUsageLog;
import com.againspring.repository.marketing.MarketingSimulationRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import com.againspring.repository.marketing.MarketingUsageLogRepository;
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
 * NOTE: ChatService 및 Report 클래스 삭제 후 스텁으로 변경됨.
 * 마케팅 모듈은 dev 전용이며 chatting/mediation 코드와 분리됨.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SimulationOrchestrator {

    private final MarketingSimulationRepository simRepo;
    private final MarketingUsageLogRepository usageLogRepo;
    private final MarketingSourceStoryRepository storyRepo;
    private final PersonaInferenceService personaService;
    private final SimulationTurnPlanner turnPlanner;
    private final VirtualUserGenerator virtualUserGenerator;

    static final String MARKETING_SYSTEM_USER_ID = "marketing_system";

    private static final int MAX_DAILY_SIMULATIONS = 10;
    private static final BigDecimal MONTHLY_BUDGET_USD = BigDecimal.valueOf(20.0);
    private static final String MODEL_HAIKU = "claude-haiku-4-5-20251001";

    // Haiku 가격 추정 (공식 기준: Input $0.80/1M, Output $4.00/1M tokens)
    private static final BigDecimal HAIKU_INPUT_PRICE = BigDecimal.valueOf(0.80)
            .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
    private static final BigDecimal HAIKU_OUTPUT_PRICE = BigDecimal.valueOf(4.00)
            .divide(BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
    // Sonnet 리포트 추정치 (실제 토큰 집계 불가, 플랫 $0.02 보수적 추정)
    private static final BigDecimal SONNET_REPORT_FLAT_COST = BigDecimal.valueOf(0.02);

    /**
     * 시뮬레이션 비동기 실행 스텁.
     * 마케팅 모듈은 chatting/mediation 인프라 제거 후 재설계 필요.
     */
    @Async("marketingExecutor")
    public void runSimulation(Long simulationId) {
        try {
            log.info("Starting simulation {} (stub implementation)", simulationId);

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

            // 4. 페르소나 추론
            String personaJson = personaService.inferPersonas(story);
            simulation.setPersonaA(personaJson);
            simRepo.save(simulation);

            // 5. 마케팅 전용 sessionId 생성
            String sessionId = "ses_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            simulation.setSessionId(sessionId);
            simRepo.save(simulation);

            // 6. 스텁: 간단한 턴 로그 생성 (실제 chatting 없음)
            List<String> conversationLog = new ArrayList<>();
            int cumulativeUserChars = 0;
            BigDecimal totalCost = BigDecimal.ZERO;
            int actualTurns = 0;
            String lastMediatorContent = null;

            for (int turn = 1; turnPlanner.shouldContinue(actualTurns, cumulativeUserChars, lastMediatorContent) && turn <= 5; turn++) {
                String aMessage = generateVirtualUserMessage(story, personaJson, conversationLog, turn);
                cumulativeUserChars += aMessage.length();

                conversationLog.add("A: " + aMessage);
                lastMediatorContent = "Mediator response stub";
                conversationLog.add("Mediator: " + lastMediatorContent);

                // 비용 추정
                totalCost = totalCost.add(estimateCost(aMessage.length() / 4, aMessage.length() / 4));
                actualTurns++;
            }

            // 7. 리포트 스텁 (skipReport)
            totalCost = totalCost.add(SONNET_REPORT_FLAT_COST);

            // 8. 시뮬레이션 완료
            simulation = simRepo.findById(simulationId).orElseThrow();
            simulation.setStatus(MarketingSimulation.Status.COMPLETED);
            simulation.setActualTurnCount(actualTurns);
            simulation.setLlmCostUsd(totalCost);
            simulation.setFinishedAt(Instant.now());
            simulation.setConversationLog(String.join("\n", conversationLog));
            simRepo.save(simulation);

            // 9. 사용 로그 기록
            usageLogRepo.save(MarketingUsageLog.builder()
                    .simulationId(simulationId)
                    .model(MODEL_HAIKU)
                    .inputTokens(cumulativeUserChars / 4)
                    .outputTokens(cumulativeUserChars / 4)
                    .costUsd(totalCost)
                    .build());

            log.info("Simulation {} completed: {} turns, cost ${}", simulationId, actualTurns, totalCost);

        } catch (Exception e) {
            log.error("Simulation {} failed", simulationId, e);
            updateSimulationFailed(simulationId, e.getMessage());
        }
    }

    private String generateVirtualUserMessage(MarketingSourceStory story, String personaJson,
                                               List<String> recentLog, int turn) {
        return virtualUserGenerator.generateLine(story, personaJson, recentLog, turn);
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
