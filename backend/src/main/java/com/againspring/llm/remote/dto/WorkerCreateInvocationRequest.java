package com.againspring.llm.remote.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkerCreateInvocationRequest {
    private final String prompt;
    private final String model;
    private final String sessionId;
    private final long timeoutMs;
}
