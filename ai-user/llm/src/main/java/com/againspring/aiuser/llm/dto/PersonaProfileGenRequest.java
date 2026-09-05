package com.againspring.aiuser.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * WP1 — {@code POST /generate/persona-profile} 요청 (.request/persona-diversity-v4/01-wp1-persona-data.md §4).
 * {@code axes}는 {age_years,gender,marital,married_years,has_kids,job_type,region,tier,voice_type,style_axes}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaProfileGenRequest {
    private String personaId;
    private String nickname;
    private Map<String, Object> axes;
    private List<String> usedPhrases;
    private String correlationId;
    private Long timeoutMs;
}
