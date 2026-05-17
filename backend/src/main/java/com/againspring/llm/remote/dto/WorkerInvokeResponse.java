package com.againspring.llm.remote.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkerInvokeResponse {
    private String text;
    private long latencyMs;
    private String correlationId;
    private String error;
    private String errorType;
}
