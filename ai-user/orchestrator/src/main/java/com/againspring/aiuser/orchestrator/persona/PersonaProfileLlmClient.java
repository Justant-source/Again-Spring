package com.againspring.aiuser.orchestrator.persona;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * llm 워커 {@code POST /generate/persona-profile} 전용 클라이언트 (01-wp1-persona-data.md §4).
 * 기존 {@code LlmAiUserClient}는 다른 WP도 함께 쓰는 공용 파일이라 충돌을 피하려고 새 클래스로
 * 분리했다 — 같은 {@code llmAiUserRestClient} 빈을 재사용할 뿐 그 파일은 건드리지 않는다.
 */
@Slf4j
@Component
public class PersonaProfileLlmClient {

    private final RestClient restClient;

    public PersonaProfileLlmClient(@Qualifier("llmAiUserRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * llm 워커 호출 결과. 실패 시 {@code response}는 null이고 {@code errorText}에 원인 텍스트
     * (HTTP 오류 응답 본문 또는 예외 메시지)를 담는다 — 호출자(PersonaProfileRegenerator)가
     * {@code LlmErrorSignatures}로 한도·인증·거절 오류인지 판별해 배치 중단 여부를 정한다.
     */
    public record ProfileResult(Map<String, Object> response, String errorText) {
        public boolean isSuccess() {
            return response != null;
        }
    }

    public ProfileResult generatePersonaProfile(
            String personaId, String nickname, PersonaQuotaPlanner.IdentityAxes axes,
            String region, String voiceType, List<String> usedPhrases) {
        try {
            Map<String, Object> axesPayload = new LinkedHashMap<>();
            axesPayload.put("age_years", axes.ageYears());
            axesPayload.put("gender", axes.gender());
            axesPayload.put("marital", axes.marital());
            axesPayload.put("married_years", axes.marriedYears());
            axesPayload.put("has_kids", axes.hasKids());
            axesPayload.put("job_type", axes.jobType());
            axesPayload.put("region", region);
            axesPayload.put("tier", axes.tier());
            axesPayload.put("voice_type", voiceType);
            axesPayload.put("style_axes", axes.styleAxes());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("personaId", personaId);
            body.put("nickname", nickname);
            body.put("axes", axesPayload);
            body.put("usedPhrases", usedPhrases == null ? List.of() : usedPhrases);
            body.put("correlationId", "persona-profile-" + personaId);

            Map<String, Object> resp = restClient.post()
                    .uri("/generate/persona-profile")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (resp == null) {
                log.warn("persona-profile generation failed for {}: empty response", personaId);
                return new ProfileResult(null, "empty response");
            }
            if (resp.containsKey("errorCode")) {
                log.warn("persona-profile generation failed for {}: {}", personaId, resp);
                String errorText = resp.get("errorCode") + ": " + resp.get("message");
                return new ProfileResult(null, errorText);
            }
            return new ProfileResult(resp, null);
        } catch (RestClientResponseException e) {
            String body2 = e.getResponseBodyAsString();
            String errorText = (body2 == null || body2.isBlank()) ? e.getMessage() : body2;
            log.warn("persona-profile call failed for {}: {}", personaId, e.getMessage());
            return new ProfileResult(null, errorText);
        } catch (Exception e) {
            log.warn("persona-profile call failed for {}: {}", personaId, e.getMessage());
            return new ProfileResult(null, e.getMessage());
        }
    }
}
