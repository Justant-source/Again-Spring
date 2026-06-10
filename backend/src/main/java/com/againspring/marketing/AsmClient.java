package com.againspring.marketing;

import com.againspring.marketing.dto.AsmJobView;
import com.againspring.marketing.dto.CreateJobRequest;
import com.againspring.marketing.dto.CreateJobResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTP client for ASM (Again-Spring-Marketing) service
 */
@Slf4j
@Component
public class AsmClient {

    private final AsmProperties asmProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AsmClient(AsmProperties asmProperties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.asmProperties = asmProperties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(asmProperties.getRequestTimeoutMs());
        factory.setReadTimeout(asmProperties.getRequestTimeoutMs());
        this.restClient = restClientBuilder
            .requestFactory(factory)
            .baseUrl(asmProperties.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + asmProperties.getApiToken())
            .build();
    }

    /**
     * Create a new job in ASM with idempotency support
     */
    public CreateJobResponse createJob(CreateJobRequest request, String idempotencyKey) {
        try {
            return restClient
                .post()
                .uri("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
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

    /**
     * Re-queue a PARTIAL/FAILED marketing job for publishing.
     * Resets NEEDS_AUTH/FAILED publications to PENDING before retrying.
     */
    public AsmJobView republish(String jobId) {
        try {
            return restClient
                .post()
                .uri("/api/v1/jobs/{jobId}/republish", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AsmJobView.class);
        } catch (Exception e) {
            log.error("Failed to republish ASM job {}", jobId, e);
            throw new AsmUnavailableException("Failed to republish ASM job: " + e.getMessage(), e);
        }
    }

    /**
     * List all platform credential statuses (secrets masked by ASM)
     */
    public JsonNode listCredentials() {
        try {
            return restClient
                .get()
                .uri("/api/v1/credentials")
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Failed to list ASM credentials", e);
            throw new AsmUnavailableException("Failed to list credentials: " + e.getMessage(), e);
        }
    }

    /**
     * Create or update credentials for a platform. ASM encrypts at rest and
     * returns the masked status. 4xx from ASM (e.g. unsupported platform,
     * missing required field) is surfaced to the caller with its status.
     */
    public JsonNode upsertCredential(String platform, JsonNode body) {
        try {
            return restClient
                .put()
                .uri("/api/v1/credentials/{platform}", platform)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to upsert ASM credential for {}", platform, e);
            throw new AsmUnavailableException("Failed to upsert credential: " + e.getMessage(), e);
        }
    }

    /**
     * Delete credentials for a platform
     */
    public void deleteCredential(String platform) {
        try {
            restClient
                .delete()
                .uri("/api/v1/credentials/{platform}", platform)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to delete ASM credential for {}", platform, e);
            throw new AsmUnavailableException("Failed to delete credential: " + e.getMessage(), e);
        }
    }

    /**
     * Extract FastAPI's {"detail": "..."} message from an ASM 4xx response body.
     */
    private String asmErrorDetail(HttpClientErrorException e) {
        try {
            JsonNode detail = objectMapper.readTree(e.getResponseBodyAsString()).get("detail");
            if (detail != null && detail.isTextual()) {
                return detail.asText();
            }
        } catch (Exception ignored) {
            // fall through to generic message
        }
        return "ASM rejected the request (" + e.getStatusCode().value() + ")";
    }
}
