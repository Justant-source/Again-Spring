package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.llm.LLMProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 마케팅 소스 스토리로부터 페르소나 추론 서비스.
 * V15.3: 익명화된 스토리 텍스트에서 두 인물의 페르소나를 LLM으로 추론.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class PersonaInferenceService {

    private final LLMProvider llmProvider;
    private final ObjectMapper objectMapper;

    /**
     * 스토리 텍스트에서 두 페르소나를 추론.
     *
     * @param story 소스 스토리 엔티티
     * @return JSON 문자열: {"personaA": {"role": "...", "gender": "...", "ageGroup": "..."}, "personaB": {...}}
     */
    public String inferPersonas(MarketingSourceStory story) {
        try {
            String prompt = buildPersonaInferencePrompt(story.getAnonymizedText());
            String response = llmProvider.invoke(prompt, "claude-haiku-4-5-20251001");

            // 응답 검증
            validateJsonResponse(response);
            return response;
        } catch (Exception e) {
            log.warn("Failed to infer personas for story {}: {}", story.getId(), e.getMessage());
            return getDefaultPersonasJson();
        }
    }

    /**
     * 페르소나 추론 프롬프트 생성.
     */
    private String buildPersonaInferencePrompt(String anonymizedText) {
        return """
            아래 관계 갈등 스토리를 읽고, 당사자 A와 B의 페르소나를 JSON 형식으로 추론해주세요.

            스토리:
            %s

            다음 JSON 구조로 응답하세요. 반드시 유효한 JSON만 반환하세요:
            {
              "personaA": {
                "role": "당사자 A의 역할/입장 (예: 아내, 친구, 부모)",
                "gender": "성별 (남성/여성/기타)",
                "ageGroup": "나이 대 (20대/30대/40대/50대+)",
                "primaryNeed": "주요 욕구 한 문장"
              },
              "personaB": {
                "role": "당사자 B의 역할/입장",
                "gender": "성별",
                "ageGroup": "나이 대",
                "primaryNeed": "주요 욕구 한 문장"
              }
            }
            """.formatted(anonymizedText);
    }

    /**
     * JSON 응답 검증.
     */
    private void validateJsonResponse(String response) throws Exception {
        objectMapper.readTree(response);
    }

    /**
     * 기본 페르소나 JSON (추론 실패 시).
     */
    private String getDefaultPersonasJson() {
        return "{\"personaA\":{\"role\":\"당사자A\",\"gender\":\"미정\",\"ageGroup\":\"미정\"}," +
               "\"personaB\":{\"role\":\"당사자B\",\"gender\":\"미정\",\"ageGroup\":\"미정\"}}";
    }
}
