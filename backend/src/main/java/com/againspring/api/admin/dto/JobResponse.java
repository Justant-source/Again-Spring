package com.againspring.api.admin.dto;

import com.againspring.domain.marketing.MarketingJob;
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
    private String remoteJobId;
    private String postId;
    private String status;
    private String phase;
    private String remoteStatus;
    private String remotePhase;
    private Double progress;
    private List<String> targets;
    private Boolean autoPublish;
    private Map<String, Object> artifacts;
    private List<Map<String, Object>> publications;
    private String errorMessage;
    private String processingDetail;
    private Instant waitingExternalSince;
    private Instant slaBreachedAt;
    private String requestedBy;
    private Integer pollFailCount;
    private Instant lastPolledAt;
    private Instant createdAt;
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
            .remoteStatus(job.getRemoteStatus())
            .remotePhase(job.getRemotePhase())
            .progress(job.getProgress())
            .targets(targets)
            .autoPublish(job.getAutoPublish())
            .artifacts(artifacts)
            .publications(publications)
            .errorMessage(job.getErrorMessage())
            .processingDetail(job.getProcessingDetail())
            .waitingExternalSince(job.getWaitingExternalSince())
            .slaBreachedAt(job.getSlaBreachedAt())
            .requestedBy(job.getRequestedBy())
            .pollFailCount(job.getPollFailCount())
            .lastPolledAt(job.getLastPolledAt())
            .createdAt(job.getCreatedAt())
            .updatedAt(job.getUpdatedAt())
            .build();
    }
}
