package com.againspring.llmworker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvokeRequest {
    private String prompt;
    private String model;
    private long timeoutMs;
    private String correlationId;
    /** Optional vision attachments. Worker caps at 1. */
    private List<InvokeImage> images;
}
