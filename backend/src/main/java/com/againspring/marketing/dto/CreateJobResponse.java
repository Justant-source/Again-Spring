package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response from ASM when creating a job
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobResponse {

    @JsonProperty("job_id")
    private String jobId;

    private String status;
}
