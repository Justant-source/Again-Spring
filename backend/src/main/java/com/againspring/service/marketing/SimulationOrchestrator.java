package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.domain.marketing.MarketingUsageLog;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.LLMException;
import com.againspring.repository.marketing.MarketingSimulationRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import com.againspring.repository.marketing.MarketingUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Random;

/**
 * 마케팅 시뮬레이션 오케스트레이터.
 * V15.3: 승인된 스토리를 기반으로 7~10턴의 AI 중재 대화를 생성.
 * 비동기 실행, 비용 제한, 턴 관리를 담당.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class SimulationOrchestrator {

    private final MarketingSimulationRepository simRepo;
    private final MarketingUsageLogRepository usageLogRepo;
    private final MarketingSourceStoryRepository storyRepo;
    private final LLMProvider llmProvider;
    private final PersonaInferenceService personaService;

    private static final int MAX_DAILY_SIMULATIONS = 10;
    private static final BigDecimal MONTHLY_BUDGET_USD = BigDecimal.valueOf(20.0);
    private static final String MODEL_HAIKU = "claude-haiku-4-5-20251001";

    // 하이쿠 모델 가격 (공식 기준)
    // Input: $0.80 / 1M tokens, Output: $4.00 / 1M tokens
    private static final BigDecimal HAIKU_INPUT_PRICE = BigDecimal.valueOf(0.80).divide(
        BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);
    private static final BigDecimal HAIKU_OUTPUT_PRICE = BigDecimal.valueOf(4.00).divide(
        BigDecimal.valueOf(1_000_000), 10, RoundingMode.HALF_UP);

    /**
     * 일일 및 월간 비용 한도 확인.
     *
     * @throws IllegalStateException 한도 초과 시
     */
    private void checkCostGuards() {
        Instant todayStart = LocalDate.now(ZoneOffset.UTC)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        Instant tomorrowStart = todayStart.plus(1, ChronoUnit.DAYS);

        long todayCount = usageLogRepo.countByCreatedAtBetween(todayStart, tomorrowStart);
        if (todayCount >= MAX_DAILY_SIMULATIONS) {
            throw new IllegalStateException("일일 시뮬레이션 한도(" + MAX_DAILY_SIMULATIONS + "건) 초과");
        }

        Instant monthStart = YearMonth.now(ZoneOffset.UTC)
            .atDay(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();
        Instant nextMonthStart = YearMonth.now(ZoneOffset.UTC)
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();

        BigDecimal monthlySpend = usageLogRepo.sumCostByCreatedAtBetween(monthStart, nextMonthStart);
        if (monthlySpend.compareTo(MONTHLY_BUDGET_USD) >= 0) {
            throw new IllegalStateException("월 예산($" + MONTHLY_BUDGET_USD + ") 초과");
        }
    }

    /**
     * 시뮬레이션 비동기 실행.
     * 7~10턴의 LLM 호출을 순차적으로 수행하고, 상태를 저장.
     *
     * @param simulationId 시뮬레이션 ID
     */
    @Async("marketingExecutor")
    @Transactional
    public void runSimulation(Long simulationId) {
        try {
            log.info("Starting simulation {}", simulationId);

            // 1. 비용 한도 확인
            checkCostGuards();

            // 2. 시뮬레이션 및 스토리 로드
            MarketingSimulation simulation = simRepo.findById(simulationId)
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simulationId));

            MarketingSourceStory story = storyRepo.findById(simulation.getSourceStoryId())
                .orElseThrow(() -> new IllegalArgumentException("Story not found: " + simulation.getSourceStoryId()));

            // 3. 시뮬레이션 상태 = RUNNING
            simulation.setStatus(MarketingSimulation.Status.RUNNING);
            simulation.setStartedAt(Instant.now());
            simRepo.save(simulation);

            // 4. 턴 수 결정 (7~10)
            Random random = new Random();
            int turnCount = 7 + random.nextInt(4);
            int actualTurn = 0;

            BigDecimal totalCost = BigDecimal.ZERO;

            // 5. 페르소나 추론 (최초 1회)
            String personasJson = personaService.inferPersonas(story);
            simulation.setPersonaA(personasJson);
            simulation.setPersonaB(personasJson);

            // 6. 턴별 대화 생성
            String conversationHistory = "";

            for (int turn = 1; turn <= turnCount; turn++) {
                try {
                    // A의 응답 생성
                    String promptA = buildTurnPromptA(story.getAnonymizedText(), conversationHistory, turn);
                    String responseA = llmProvider.invoke(promptA, MODEL_HAIKU);

                    // 비용 로깅 (간략한 추정)
                    BigDecimal costA = estimateCost(promptA.length() / 4, responseA.length() / 4);
                    totalCost = totalCost.add(costA);

                    conversationHistory += "A: " + responseA + "\n";

                    // B의 응답 생성
                    String promptB = buildTurnPromptB(story.getAnonymizedText(), conversationHistory, turn);
                    String responseB = llmProvider.invoke(promptB, MODEL_HAIKU);

                    BigDecimal costB = estimateCost(promptB.length() / 4, responseB.length() / 4);
                    totalCost = totalCost.add(costB);

                    conversationHistory += "B: " + responseB + "\n";

                    // 중재자의 응답 생성
                    String promptMediator = buildMediatorPrompt(conversationHistory, turn);
                    String responseMediator = llmProvider.invoke(promptMediator, MODEL_HAIKU);

                    BigDecimal costMediator = estimateCost(promptMediator.length() / 4, responseMediator.length() / 4);
                    totalCost = totalCost.add(costMediator);

                    conversationHistory += "Mediator: " + responseMediator + "\n";

                    actualTurn = turn;

                } catch (LLMException e) {
                    log.warn("LLM error on turn {}: {}", turn, e.getMessage());
                    // 턴 실패 시에도 진행하되, 카운트는 기록
                    break;
                }
            }

            // 7. 시뮬레이션 완료
            simulation.setStatus(MarketingSimulation.Status.COMPLETED);
            simulation.setActualTurnCount(actualTurn);
            simulation.setLlmCostUsd(totalCost);
            simulation.setFinishedAt(Instant.now());
            simulation.setConversationLog(conversationHistory);
            simRepo.save(simulation);

            // 8. 사용 로그 기록
            MarketingUsageLog usageLog = MarketingUsageLog.builder()
                .simulationId(simulationId)
                .model(MODEL_HAIKU)
                .inputTokens(conversationHistory.length() / 4)
                .outputTokens(conversationHistory.length() / 4)
                .costUsd(totalCost)
                .build();
            usageLogRepo.save(usageLog);

            log.info("Simulation {} completed: {} turns, cost ${}", simulationId, actualTurn, totalCost);

        } catch (IllegalStateException e) {
            log.warn("Simulation {} blocked by cost guards: {}", simulationId, e.getMessage());
            updateSimulationFailed(simulationId, e.getMessage());
        } catch (Exception e) {
            log.error("Simulation {} failed", simulationId, e);
            updateSimulationFailed(simulationId, e.getMessage());
        }
    }

    /**
     * 시뮬레이션 상태를 FAILED로 설정.
     */
    @Transactional
    private void updateSimulationFailed(Long simulationId, String errorMessage) {
        simRepo.findById(simulationId).ifPresent(sim -> {
            sim.setStatus(MarketingSimulation.Status.FAILED);
            sim.setErrorMessage(errorMessage);
            sim.setFinishedAt(Instant.now());
            simRepo.save(sim);
        });
    }

    /**
     * 당사자 A의 턴 프롬프트 생성.
     */
    private String buildTurnPromptA(String storyContext, String conversationHistory, int turnNumber) {
        return """
            ### 역할
            당신은 관계 갈등에 있는 당사자 A입니다. 자신의 입장과 감정을 진심 있게 표현하세요.

            ### 상황
            %s

            ### 현재까지의 대화
            %s

            ### 지시사항
            - 이 턴에서 당신의 입장을 1~3문장으로 표현하세요.
            - 상대의 말을 경청하려는 자세를 보이세요.
            - 공격적이지 않으면서도 자신의 욕구를 명확히 하세요.

            당신의 응답:
            """.formatted(storyContext, conversationHistory);
    }

    /**
     * 당사자 B의 턴 프롬프트 생성.
     */
    private String buildTurnPromptB(String storyContext, String conversationHistory, int turnNumber) {
        return """
            ### 역할
            당신은 관계 갈등에 있는 당사자 B입니다. 자신의 입장과 감정을 진심 있게 표현하세요.

            ### 상황
            %s

            ### 현재까지의 대화
            %s

            ### 지시사항
            - 이 턴에서 당신의 입장을 1~3문장으로 표현하세요.
            - 상대의 말을 경청하려는 자세를 보이세요.
            - 공격적이지 않으면서도 자신의 욕구를 명확히 하세요.

            당신의 응답:
            """.formatted(storyContext, conversationHistory);
    }

    /**
     * 중재자의 턴 프롬프트 생성.
     */
    private String buildMediatorPrompt(String conversationHistory, int turnNumber) {
        return """
            ### 역할
            당신은 중립적인 중재자입니다. 양쪽의 입장을 정리하고 공감하세요.

            ### 현재까지의 대화
            %s

            ### 지시사항
            - A와 B의 감정을 각각 인정하세요.
            - 공통점과 차이점을 간단히 정리하세요.
            - 다음 단계의 대화를 위한 건설적인 방향을 제시하세요.
            - 1~2문장으로 중재 의견을 제시하세요.

            중재자의 응답:
            """.formatted(conversationHistory);
    }

    /**
     * 토큰 기반 비용 추정 (하이쿠 모델).
     * 간략한 추정값: 4자 ≈ 1토큰.
     */
    private BigDecimal estimateCost(int inputTokens, int outputTokens) {
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens).multiply(HAIKU_INPUT_PRICE);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens).multiply(HAIKU_OUTPUT_PRICE);
        return inputCost.add(outputCost).setScale(4, RoundingMode.HALF_UP);
    }
}
