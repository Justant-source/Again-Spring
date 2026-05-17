package com.againspring.llm.remote.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkerInvokeRequest {
    private final String prompt;
    private final String model;
    @JsonProperty("timeoutMs")
    private final long timeoutMs;
    @JsonProperty("correlationId")
    private final String correlationId;
}
