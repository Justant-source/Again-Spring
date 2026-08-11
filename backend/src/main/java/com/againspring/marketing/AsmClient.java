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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * HTTP client for ASM (Again-Spring-Marketing) service
 */
@Slf4j
@Component
public class AsmClient {

    private final AsmProperties asmProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    /** Long-read client for platform stats collect (Playwright / Analytics). */
    private final RestClient statsRestClient;

    public AsmClient(AsmProperties asmProperties, RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.asmProperties = asmProperties;
        this.objectMapper = objectMapper;
        this.restClient = buildClient(restClientBuilder, asmProperties, asmProperties.getRequestTimeoutMs());
        int statsTimeout = asmProperties.getStatsRequestTimeoutMs() > 0
            ? asmProperties.getStatsRequestTimeoutMs()
            : 300_000;
        this.statsRestClient = buildClient(restClientBuilder, asmProperties, statsTimeout);
    }

    private static RestClient buildClient(
            RestClient.Builder restClientBuilder, AsmProperties props, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int connectMs = props.getRequestTimeoutMs() > 0 ? props.getRequestTimeoutMs() : 10_000;
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readTimeoutMs);
        return restClientBuilder
            .clone()
            .requestFactory(factory)
            .baseUrl(props.getBaseUrl())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiToken())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    /**
     * Create a new job in ASM with idempotency support
     * Retries on network/timeout/5xx errors with exponential backoff (1s/2s/4s)
     */
    public CreateJobResponse createJob(CreateJobRequest request, String idempotencyKey) {
        return retryWithBackoff(
            () -> restClient
                .post()
                .uri("/api/v1/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(CreateJobResponse.class),
            "create ASM job"
        );
    }

    /**
     * Get job status from ASM
     * Retries on network/timeout/5xx errors with exponential backoff (1s/2s/4s)
     */
    public AsmJobView getJob(String jobId) {
        return retryWithBackoff(
            () -> restClient
                .get()
                .uri("/api/v1/jobs/{jobId}", jobId)
                .retrieve()
                .body(AsmJobView.class),
            "get ASM job " + jobId
        );
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
     * Upload/replace an artifact on ASM (currently used only for operator-set
     * custom thumbnails — ASM restricts the writable name pattern to
     * {@code {platform}__customcover.{png,jpg,jpeg}}). Not retried: the byte
     * body is safely re-sendable, but a partial write on the ASM side plus a
     * blind retry isn't worth the complexity for a small, rarely-called
     * upload — same non-retried precedent as {@link #getArtifact}.
     */
    public void putArtifact(String jobId, String name, byte[] bytes, String contentType) {
        try {
            restClient
                .put()
                .uri("/api/v1/jobs/{jobId}/artifacts/{name}", jobId, name)
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to upload ASM artifact {}/{}", jobId, name, e);
            throw new AsmUnavailableException("Failed to upload artifact: " + e.getMessage(), e);
        }
    }

    /**
     * Trigger publishing for a job
     * Retries on network/timeout/5xx errors with exponential backoff (1s/2s/4s)
     */
    public AsmJobView publish(String jobId) {
        return retryWithBackoff(
            () -> restClient
                .post()
                .uri("/api/v1/jobs/{jobId}/publish", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AsmJobView.class),
            "publish ASM job " + jobId
        );
    }

    /**
     * Re-queue a PARTIAL/FAILED marketing job for publishing.
     * Resets NEEDS_AUTH/FAILED publications to PENDING before retrying.
     * Retries on network/timeout/5xx errors with exponential backoff (1s/2s/4s)
     */
    public AsmJobView republish(String jobId) {
        return retryWithBackoff(
            () -> restClient
                .post()
                .uri("/api/v1/jobs/{jobId}/republish", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AsmJobView.class),
            "republish ASM job " + jobId
        );
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
     * WaggleBot TTS voice catalog (via ASM proxy).
     * Returns {@code { defaultVoice, voices:[{key,label,gender,sampleUrl,hasSample,...}] }}.
     */
    public JsonNode listWaggleVoices() {
        try {
            return restClient
                .get()
                .uri("/api/v1/waggle/voices")
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Failed to list WaggleBot voices via ASM", e);
            throw new AsmUnavailableException("Failed to list WaggleBot voices: " + e.getMessage(), e);
        }
    }

    /**
     * Stream a WaggleBot voice sample file through ASM.
     * {@code path} must be a WB media path like {@code /api/media/voices/...}.
     */
    public ResponseEntity<Resource> getWaggleVoiceSample(String path) {
        try {
            return restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/waggle/voice-sample")
                    .queryParam("path", path)
                    .build())
                .retrieve()
                .toEntity(Resource.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to fetch WaggleBot voice sample {}", path, e);
            throw new AsmUnavailableException("Failed to fetch voice sample: " + e.getMessage(), e);
        }
    }

    /**
     * Best-effort platform stats collect (Phase 2.6).
     * Body: {@code {"job_ids":[...], "lookback_days":14, "limit":40}}.
     * Returns {@code {"results":[...], "count":N}} — partial failures included per row.
     */
    public JsonNode collectStats(JsonNode body) {
        try {
            return statsRestClient
                .post()
                .uri("/api/v1/stats/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (ResourceAccessException e) {
            log.error("ASM stats collect timed out or unreachable", e);
            throw new AsmUnavailableException(
                "통계 수집 시간 초과/연결 실패 — 잠시 후 다시 시도하거나 limit를 줄여 주세요: "
                    + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to collect ASM platform stats", e);
            throw new AsmUnavailableException("Failed to collect platform stats: " + e.getMessage(), e);
        }
    }

    /**
     * YouTube OAuth — /start: Google 인증 URL 생성.
     * body: {"redirect_uri": "..."}
     * 반환: {"auth_url": "..."}
     */
    public JsonNode youtubeOauthStart(JsonNode body) {
        try {
            return restClient
                .post()
                .uri("/api/v1/credentials/youtube_shorts/oauth/start")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to start YouTube OAuth", e);
            throw new AsmUnavailableException("Failed to start YouTube OAuth: " + e.getMessage(), e);
        }
    }

    /**
     * YouTube OAuth — /exchange: authorization code 교환 → refresh_token 저장.
     * body: {"code": "...", "state": "..."}
     * 반환: CredentialStatus (마스킹)
     */
    public JsonNode youtubeOauthExchange(JsonNode body) {
        try {
            return restClient
                .post()
                .uri("/api/v1/credentials/youtube_shorts/oauth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to exchange YouTube OAuth code", e);
            throw new AsmUnavailableException("Failed to exchange YouTube OAuth code: " + e.getMessage(), e);
        }
    }

    /**
     * Retry helper with exponential backoff (1s/2s/4s).
     * Only retries on network/timeout/5xx errors.
     * Immediately fails on 4xx errors (auth failures, validation errors, etc).
     */
    private <T> T retryWithBackoff(RetryableOperation<T> operation, String operationName) {
        int[] backoffDelays = {1000, 2000, 4000}; // milliseconds
        Exception lastException = null;

        // Initial attempt
        try {
            return operation.execute();
        } catch (HttpClientErrorException e) {
            // 4xx errors are not retryable (e.g., 401 auth failed)
            log.error("ASM returned non-retryable error {}: {}", operationName, e.getStatusCode());
            throw new AsmUnavailableException("Failed to " + operationName + ": " + e.getMessage(), e);
        } catch (Exception e) {
            lastException = e;
            if (!isRetryable(e)) {
                log.error("Failed to {}: {}", operationName, e.getMessage(), e);
                throw new AsmUnavailableException("Failed to " + operationName + ": " + e.getMessage(), e);
            }
            log.debug("Retryable error on {} (attempt 1/3): {}", operationName, e.getMessage());
        }

        // Retry attempts with backoff
        for (int attempt = 2; attempt <= 3; attempt++) {
            try {
                Thread.sleep(backoffDelays[attempt - 2]);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Retry sleep interrupted for {}", operationName, ie);
                throw new AsmUnavailableException("Failed to " + operationName + ": retry interrupted", ie);
            }

            try {
                log.debug("Retrying {} (attempt {}/3)...", operationName, attempt);
                return operation.execute();
            } catch (HttpClientErrorException e) {
                // 4xx errors are not retryable
                log.error("ASM returned non-retryable error {} on retry {}: {}", operationName, attempt, e.getStatusCode());
                throw new AsmUnavailableException("Failed to " + operationName + ": " + e.getMessage(), e);
            } catch (Exception e) {
                if (!isRetryable(e)) {
                    log.error("Non-retryable error on {} (attempt {}/3): {}", operationName, attempt, e.getMessage(), e);
                    throw new AsmUnavailableException("Failed to " + operationName + ": " + e.getMessage(), e);
                }
                lastException = e;
                log.debug("Retryable error on {} (attempt {}/3): {}", operationName, attempt, e.getMessage());
            }
        }

        // All retries exhausted
        log.error("All 3 retry attempts exhausted for {}", operationName, lastException);
        throw new AsmUnavailableException("Failed to " + operationName + " after 3 retries: " + lastException.getMessage(), lastException);
    }

    /**
     * Determines if an exception is retryable (network/timeout/5xx).
     * Non-retryable: 4xx errors (handled separately), other non-network errors.
     */
    private boolean isRetryable(Exception e) {
        // Network errors
        if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
            return true;
        }
        // ResourceAccessException includes connection timeouts, read timeouts, etc.
        if (e instanceof ResourceAccessException) {
            return true;
        }
        // 5xx errors (handled by RestClient as exception)
        // Note: RestClient throws HttpServerErrorException for 5xx, which is a HttpClientErrorException subclass
        // We'll treat it as retryable if it's a 5xx
        if (e instanceof HttpClientErrorException) {
            HttpClientErrorException httpEx = (HttpClientErrorException) e;
            if (httpEx.getStatusCode().is5xxServerError()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Functional interface for retryable operations.
     */
    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws Exception;
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
