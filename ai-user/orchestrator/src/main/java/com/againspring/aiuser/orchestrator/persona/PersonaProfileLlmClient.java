package com.againspring.aiuser.orchestrator.persona;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public Optional<Map<String, Object>> generatePersonaProfile(
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
            if (resp == null || resp.containsKey("errorCode")) {
                log.warn("persona-profile generation failed for {}: {}", personaId, resp);
                return Optional.empty();
            }
            return Optional.of(resp);
        } catch (Exception e) {
            log.warn("persona-profile call failed for {}: {}", personaId, e.getMessage());
            return Optional.empty();
        }
    }
}
