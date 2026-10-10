package com.againspring.llm.remote.dto;

import com.againspring.llm.LlmImage;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WorkerInvokeRequest {
    private final String prompt;
    private final String model;
    @JsonProperty("timeoutMs")
    private final long timeoutMs;
    @JsonProperty("correlationId")
    private final String correlationId;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<LlmImage> images;
    /** Claude CLI {@code --effort}. Null이면 워커가 플래그를 생략한다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String effort;
}
