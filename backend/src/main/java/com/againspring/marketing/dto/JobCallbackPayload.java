package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Callback payload from ASM (Again-Spring-Marketing) service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobCallbackPayload {
    @JsonProperty("job_id")
    private String jobId;

    private String status;

    private String phase;

    private Double progress;

    private Map<String, Object> artifacts;

    private List<Map<String, Object>> publications;

    private String event;

    private String error;

    /** Additive renderer quality facts; raw prompt/LLM output must never be included. */
    @JsonAlias("generation_diagnostics")
    private Map<String, Object> diagnostics;

    @JsonProperty("actual_duration_ms")
    private Long actualDurationMs;

    @JsonProperty("failure_code")
    private String failureCode;

    @JsonProperty("failure_stage")
    private String failureStage;

    private Boolean retryable;

    @JsonProperty("error_summary")
    private String errorSummary;
}
