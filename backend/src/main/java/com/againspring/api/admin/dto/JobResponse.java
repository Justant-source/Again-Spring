package com.againspring.api.admin.dto;

import com.againspring.domain.marketing.MarketingJob;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for marketing job
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {

    private Long id;

    @JsonProperty("remote_job_id")
    private String remoteJobId;

    @JsonProperty("post_id")
    private String postId;

    private String status;

    private String phase;

    private Double progress;

    private List<String> targets;

    @JsonProperty("auto_publish")
    private Boolean autoPublish;

    private Map<String, Object> artifacts;

    private List<Map<String, Object>> publications;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("requested_by")
    private String requestedBy;

    @JsonProperty("poll_fail_count")
    private Integer pollFailCount;

    @JsonProperty("last_polled_at")
    private Instant lastPolledAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    /**
     * Convert MarketingJob entity to response DTO
     */
    public static JobResponse from(MarketingJob job) {
        ObjectMapper mapper = new ObjectMapper();

        List<String> targets = null;
        Map<String, Object> artifacts = null;
        List<Map<String, Object>> publications = null;

        try {
            if (job.getTargets() != null) {
                targets = mapper.readValue(job.getTargets(), new TypeReference<List<String>>() {});
            }
            if (job.getArtifacts() != null) {
                artifacts = mapper.readValue(job.getArtifacts(), new TypeReference<Map<String, Object>>() {});
            }
            if (job.getPublications() != null) {
                publications = mapper.readValue(job.getPublications(), new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception e) {
            // Log or handle JSON parsing errors
        }

        return JobResponse.builder()
            .id(job.getId())
            .remoteJobId(job.getRemoteJobId())
            .postId(job.getPostId())
            .status(job.getStatus())
            .phase(job.getPhase())
            .progress(job.getProgress())
            .targets(targets)
            .autoPublish(job.getAutoPublish())
            .artifacts(artifacts)
            .publications(publications)
            .errorMessage(job.getErrorMessage())
            .requestedBy(job.getRequestedBy())
            .pollFailCount(job.getPollFailCount())
            .lastPolledAt(job.getLastPolledAt())
            .createdAt(job.getCreatedAt())
            .updatedAt(job.getUpdatedAt())
            .build();
    }
}
