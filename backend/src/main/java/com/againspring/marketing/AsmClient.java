package com.againspring.marketing;

import com.againspring.marketing.dto.AsmJobView;
import com.againspring.marketing.dto.CreateJobRequest;
import com.againspring.marketing.dto.CreateJobResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for ASM (Again-Spring-Marketing) service
 */
@Slf4j
@Component
public class AsmClient {

    private final AsmProperties asmProperties;
    private final RestClient restClient;

    public AsmClient(AsmProperties asmProperties, RestClient.Builder restClientBuilder) {
        this.asmProperties = asmProperties;
        this.restClient = restClientBuilder
            .baseUrl(asmProperties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + asmProperties.getApiToken())
            .build();
    }

    /**
     * Create a new job in ASM
     */
    public CreateJobResponse createJob(CreateJobRequest request) {
        try {
            return restClient
                .post()
                .uri("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(CreateJobResponse.class);
        } catch (Exception e) {
            log.error("Failed to create ASM job", e);
            throw new AsmUnavailableException("Failed to create ASM job: " + e.getMessage(), e);
        }
    }

    /**
     * Get job status from ASM
     */
    public AsmJobView getJob(String jobId) {
        try {
            return restClient
                .get()
                .uri("/api/v1/jobs/{jobId}", jobId)
                .retrieve()
                .body(AsmJobView.class);
        } catch (Exception e) {
            log.warn("Failed to poll ASM job {}: {}", jobId, e.getMessage());
            throw new AsmUnavailableException("Failed to poll ASM job: " + e.getMessage(), e);
        }
    }

    /**
     * Download artifact from ASM as a raw byte resource
     */
    public ResponseEntity<Resource> getArtifact(String jobId, String name) {
        try {
            return restClient
                .get()
                .uri("/api/v1/jobs/{jobId}/artifacts/{name}", jobId, name)
                .retrieve()
                .toEntity(Resource.class);
        } catch (Exception e) {
            log.error("Failed to fetch ASM artifact {}/{}", jobId, name, e);
            throw new AsmUnavailableException("Failed to fetch artifact: " + e.getMessage(), e);
        }
    }

    /**
     * Trigger publishing for a job
     */
    public AsmJobView publish(String jobId) {
        try {
            return restClient
                .post()
                .uri("/api/v1/jobs/{jobId}/publish", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AsmJobView.class);
        } catch (Exception e) {
            log.error("Failed to publish ASM job {}", jobId, e);
            throw new AsmUnavailableException("Failed to publish ASM job: " + e.getMessage(), e);
        }
    }
}
