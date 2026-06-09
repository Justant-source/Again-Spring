package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Job view from ASM polling response
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsmJobView {

    @JsonProperty("job_id")
    private String jobId;

    private String status;

    private String phase;

    private Double progress;

    private Map<String, Object> artifacts;

    private List<Map<String, Object>> publications;

    private String error;
}
