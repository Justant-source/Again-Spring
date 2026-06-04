package com.againspring.aiuser.llm.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaGenRequest {
    private String prompt;           // raw prompt from PersonaFactory
    private String correlationId;
    private Long timeoutMs;
}
