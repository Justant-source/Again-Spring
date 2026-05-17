package com.againspring.llmworker.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateInvocationRequest {
    private String prompt;
    private String model;
    private String sessionId;
    private long timeoutMs;
}
