package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.llm.LLMProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 마케팅 시뮬레이션용 가상 사용자 A 메시지 생성기.
 * Haiku 모델로 스토리·페르소나를 바탕으로 1~3문장의 A 발화를 생성.
 * 위기·금지어 가드를 프롬프트 수준에서 명시적으로 적용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class VirtualUserGenerator {

    private final LLMProvider llmProvider;

    private static final String MODEL_HAIKU = "claude-haiku-4-5-20251001";

    /**
     * 가상 사용자 A의 메시지를 생성.
     *
     * @param story      소스 스토리
     * @param personaJson 페르소나 JSON 문자열
     * @param recentLog   최근 대화 로그 (각 항목: "A: ...", "Mediator: ...")
     * @param turnIdx     현재 턴 번호 (1-based)
     * @return 생성된 A의 메시지
     */
    public String generateLine(MarketingSourceStory story, String personaJson,
                               List<String> recentLog, int turnIdx) {
        String prompt = buildPrompt(story.getAnonymizedText(), personaJson, recentLog, turnIdx);
        try {
            String result = llmProvider.invoke(prompt, MODEL_HAIKU);
            if (result == null || result.isBlank()) {
                return getFallbackMessage(turnIdx);
            }
            return result.strip();
        } catch (Exception e) {
            log.warn("VirtualUserGenerator failed at turn {}: {}", turnIdx, e.getMessage());
            return getFallbackMessage(turnIdx);
        }
    }

    private String buildPrompt(String storyText, String personaJson,
                               List<String> recentLog, int turnIdx) {
        String historySection = recentLog.isEmpty()
                ? "(첫 발화 — 중재자가 곧 대화를 시작할 예정입니다)"
                : String.join("\n", recentLog.size() > 6
                        ? recentLog.subList(recentLog.size() - 6, recentLog.size())
                        : recentLog);

        return """
                ### 역할
                당신은 관계 갈등 상황의 당사자 A입니다. AI 중재자와 1:1 대화를 나누고 있습니다.

                ### 절대 금지 (위반 시 응답 거부)
                - 폭력, 자해, 자살, 아동학대 관련 키워드 일체
                - 법률 용어 (과실비율, 판결, 유죄, 가해자, 피해자, 고소 등)
                - 진단명 (나르시시스트, 소시오패스, 가스라이팅, PTSD, 트라우마 등)
                - 승패 표현 (이겼다, 졌다, 맞다, 틀렸다 등)

                ### 상황 요약
                %s

                ### 당사자 A 페르소나
                %s

                ### 최근 대화 (%d턴 중)
                %s

                ### 지시사항
                - 당사자 A로서 중재자에게 1~3문장으로 진실되게 이야기하세요.
                - 감정과 상황을 솔직하게 표현하되, 비공격적으로 자신의 관점을 전달하세요.
                - 중재자의 질문이 있다면 성실하게 답변하세요.
                - 응답만 출력하세요 (역할 설명, 서론 없이).

                당사자 A의 응답:
                """.formatted(storyText, personaJson, turnIdx, historySection);
    }

    private String getFallbackMessage(int turnIdx) {
        return getFallbackSafeMessage(turnIdx);
    }

    /** crisis 차단 재시도 시 SimulationOrchestrator에서 호출. */
    public String getFallbackSafeMessage(int turnIdx) {
        if (turnIdx <= 1) {
            return "사실 이 상황이 많이 힘들었어요. 어디서부터 이야기를 시작해야 할지 모르겠지만, 서로를 이해하고 싶다는 마음은 분명해요.";
        }
        return "말씀하신 부분 잘 생각해봤어요. 결국 저도 이 관계가 더 나아지길 바라는 마음이 가장 크네요.";
    }
}
