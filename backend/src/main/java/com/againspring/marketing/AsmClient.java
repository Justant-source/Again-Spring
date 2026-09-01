package com.againspring.marketing;

import com.againspring.marketing.dto.AsmJobView;
import com.againspring.marketing.dto.CreateJobRequest;
import com.againspring.marketing.dto.CreateJobResponse;
import com.againspring.marketing.dto.XInboxResponse;
import com.againspring.marketing.dto.XOutboundCandidatesResponse;
import com.againspring.marketing.dto.XPublishRequest;
import com.againspring.marketing.dto.XPublishResponse;
import com.againspring.marketing.dto.XRitualRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
     * WaggleBot BGM track catalog (via ASM proxy).
     * Returns {@code { tracks:[{emotion,file,path,durationSec?}] }}.
     */
    public JsonNode listWaggleBgmTracks() {
        try {
            return restClient
                .get()
                .uri("/api/v1/waggle/bgm/tracks")
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Failed to list WaggleBot BGM tracks via ASM", e);
            throw new AsmUnavailableException("Failed to list WaggleBot BGM tracks: " + e.getMessage(), e);
        }
    }

    /**
     * WaggleBot SFX mapping (via ASM proxy).
     * Returns {@code { events:[...], maxPerVideo:N, library:[...] }}.
     */
    public JsonNode getWaggleSfxMapping() {
        try {
            return restClient
                .get()
                .uri("/api/v1/waggle/sfx/mapping")
                .retrieve()
                .body(JsonNode.class);
        } catch (Exception e) {
            log.error("Failed to get WaggleBot SFX mapping via ASM", e);
            throw new AsmUnavailableException("Failed to get WaggleBot SFX mapping: " + e.getMessage(), e);
        }
    }

    /**
     * Update WaggleBot SFX mapping (via ASM proxy).
     * Body: {@code { events:[...], maxPerVideo:N }}.
     */
    public JsonNode putWaggleSfxMapping(JsonNode body) {
        try {
            return restClient
                .put()
                .uri("/api/v1/waggle/sfx/mapping")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to update WaggleBot SFX mapping via ASM", e);
            throw new AsmUnavailableException("Failed to update WaggleBot SFX mapping: " + e.getMessage(), e);
        }
    }

    /**
     * 배경음악 전역 사용 여부를 바꾼다. false 면 어떤 렌더에도 BGM 이 들어가지 않는다.
     * 고르는 기능(카탈로그·잡별 bgmTrack)은 그대로 두고 소비만 막는 스위치다.
     */
    public JsonNode putWaggleBgmSettings(JsonNode body) {
        try {
            return restClient
                .put()
                .uri("/api/v1/waggle/bgm/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to update WaggleBot BGM settings via ASM", e);
            throw new AsmUnavailableException("Failed to update BGM settings: " + e.getMessage(), e);
        }
    }

    /**
     * Stream a WaggleBot BGM sample file through ASM.
     * {@code path} must be a WB media path like {@code /api/media/bgm/...}.
     */
    public ResponseEntity<Resource> getWaggleBgmSample(String path) {
        try {
            return restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/waggle/bgm/sample")
                    .queryParam("path", path)
                    .build())
                .retrieve()
                .toEntity(Resource.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), asmErrorDetail(e), e);
        } catch (Exception e) {
            log.error("Failed to fetch WaggleBot BGM sample {}", path, e);
            throw new AsmUnavailableException("Failed to fetch BGM sample: " + e.getMessage(), e);
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

    public record XPublishResult(boolean ok, String tweetId, String url, String photo) {}

    public record XInboxItem(
        String tweetId,
        String parentTweetId,
        String ourPostTweetId,
        String authorHandle,
        String text,
        Instant createdAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record XOutboundCandidate(
        String tweetId,
        String authorHandle,
        String text,
        int replyCount,
        double ageHours,
        boolean alreadyRepliedByUs,
        String ourReplyTweetId,
        boolean hasVideo,
        boolean hasPhoto,
        String photoJpegBase64,
        List<String> peerReplies
    ) {
        public XOutboundCandidate {
            if (peerReplies == null) {
                peerReplies = List.of();
            }
        }
    }

    /**
     * Publish text (and optional image) to X. {@code replyToTweetId} null = original post.
     * Retries network/5xx; 4xx is logged and thrown as {@link AsmUnavailableException}.
     */
    public XPublishResult publishX(String text, String replyToTweetId, String imageBase64, String imageMime) {
        XPublishResponse body = retryWithBackoff(
            () -> restClient
                .post()
                .uri("/api/v1/x/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new XPublishRequest(text, imageBase64, imageMime, replyToTweetId))
                .retrieve()
                .body(XPublishResponse.class),
            "publish X"
        );
        return toPublishResult(body);
    }

    /**
     * Ritual morning/night photo post via ASM local CC0 pool.
     */
    public XPublishResult publishRitual(String slot, String text) {
        XPublishResponse body = retryWithBackoff(
            () -> restClient
                .post()
                .uri("/api/v1/x/ritual")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new XRitualRequest(slot, text))
                .retrieve()
                .body(XPublishResponse.class),
            "publish X ritual"
        );
        return toPublishResult(body);
    }

    /**
     * Recent replies on our posts (not our own replies). {@code sinceMinutes} lookback.
     */
    public List<XInboxItem> listXInbox(int sinceMinutes) {
        try {
            XInboxResponse body = statsRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/x/inbox")
                    .queryParam("sinceMinutes", sinceMinutes)
                    .build())
                .retrieve()
                .body(XInboxResponse.class);
            return mapInbox(body);
        } catch (HttpClientErrorException e) {
            log.error("ASM x inbox 4xx {}: {}", e.getStatusCode(), e.getMessage());
            throw new AsmUnavailableException("Failed to list X inbox: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to list X inbox", e);
            throw new AsmUnavailableException("Failed to list X inbox: " + e.getMessage(), e);
        }
    }

    /**
     * Mutual-follow hot posts for outbound replies. Playwright scrape often exceeds
     * the default 30s ASM timeout (measured ~49s) — use the long-read client.
     */
    public List<XOutboundCandidate> listXOutboundCandidates(int minReplies, int maxAgeHours) {
        try {
            XOutboundCandidatesResponse body = statsRestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/x/outbound-candidates")
                    .queryParam("minReplies", minReplies)
                    .queryParam("maxAgeHours", maxAgeHours)
                    .build())
                .retrieve()
                .body(XOutboundCandidatesResponse.class);
            return mapOutbound(body);
        } catch (HttpClientErrorException e) {
            log.error("ASM x outbound-candidates 4xx {}: {}", e.getStatusCode(), e.getMessage());
            throw new AsmUnavailableException("Failed to list X outbound candidates: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to list X outbound candidates", e);
            throw new AsmUnavailableException("Failed to list X outbound candidates: " + e.getMessage(), e);
        }
    }

    private static XPublishResult toPublishResult(XPublishResponse body) {
        if (body == null) {
            return new XPublishResult(false, null, null, null);
        }
        return new XPublishResult(body.succeeded(), body.tweetId(), body.url(), body.photo());
    }

    private static List<XInboxItem> mapInbox(XInboxResponse body) {
        if (body == null || body.items() == null) {
            return List.of();
        }
        List<XInboxItem> out = new ArrayList<>();
        for (XInboxResponse.Item it : body.items()) {
            if (it == null || it.tweetId() == null || it.tweetId().isBlank()) {
                continue;
            }
            out.add(new XInboxItem(
                it.tweetId(),
                it.parentTweetId(),
                it.ourPostTweetId(),
                it.authorHandle(),
                it.text(),
                it.createdAt()));
        }
        return out;
    }

    private static List<XOutboundCandidate> mapOutbound(XOutboundCandidatesResponse body) {
        if (body == null || body.items() == null) {
            return List.of();
        }
        List<XOutboundCandidate> out = new ArrayList<>();
        for (XOutboundCandidatesResponse.Item it : body.items()) {
            if (it == null || it.tweetId() == null || it.tweetId().isBlank()) {
                continue;
            }
            out.add(new XOutboundCandidate(
                it.tweetId(),
                it.authorHandle(),
                it.text(),
                it.replyCount() == null ? 0 : it.replyCount(),
                it.ageHours() == null ? 0.0 : it.ageHours(),
                Boolean.TRUE.equals(it.alreadyRepliedByUs()),
                it.ourReplyTweetId(),
                Boolean.TRUE.equals(it.hasVideo()),
                Boolean.TRUE.equals(it.hasPhoto()),
                it.photoJpegBase64(),
                it.peerReplies()));
        }
        return out;
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
