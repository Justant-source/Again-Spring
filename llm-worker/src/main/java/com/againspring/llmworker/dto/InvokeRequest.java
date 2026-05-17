package com.againspring.llmworker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InvokeRequest {
    private String prompt;
    private String model;
    private long timeoutMs;
    private String correlationId;
}
