package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
}
