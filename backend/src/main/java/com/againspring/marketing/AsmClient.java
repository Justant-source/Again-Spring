package com.againspring.marketing;

import com.againspring.marketing.dto.AsmJobView;
import com.againspring.marketing.dto.CreateJobRequest;
import com.againspring.marketing.dto.CreateJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for ASM (Again-Spring-Marketing) service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsmClient {

    private final AsmProperties asmProperties;
    private RestClient restClient;

    private RestClient getRestClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                .baseUrl(asmProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + asmProperties.getApiToken())
                .build();
        }
        return restClient;
    }

    /**
     * Create a new job in ASM
     */
    public CreateJobResponse createJob(CreateJobRequest request) {
        try {
            return getRestClient()
                .post()
                .uri("/api/v1/jobs")
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
            return getRestClient()
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
     * Trigger publishing for a job
     */
    public AsmJobView publish(String jobId) {
        try {
            return getRestClient()
                .post()
                .uri("/api/v1/jobs/{jobId}/publish", jobId)
                .retrieve()
                .body(AsmJobView.class);
        } catch (Exception e) {
            log.error("Failed to publish ASM job {}", jobId, e);
            throw new AsmUnavailableException("Failed to publish ASM job: " + e.getMessage(), e);
        }
    }
}
