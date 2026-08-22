package com.againspring.aiuser.orchestrator.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI-User ML 서비스 클라이언트 (WSL, RTX 3090, port 8201).
 * /rerank: Best-of-N 초안 중 KcELECTRA+KatFishNet 인간다움 점수 최고 winner 선택.
 * /corpus/ingest: 게시 완료 텍스트를 AI negative 코퍼스에 push.
 * enabled=false 기본 — feature flag로 점진 롤아웃. WSL 다운 시 graceful skip.
 */
@Slf4j
@Component
public class AiUserMlClient {

    private final String baseUrl;
    private final String apiToken;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai-user-ml.enabled:false}")
    private boolean enabled;

    @Value("${ai-user-ml.collect:false}")
    private boolean collect;

    @Value("${ai-user-ml.best-of-n:4}")
    private int bestOfN;

    @Value("${ai-user-ml.enabled-communities:}")
    private String enabledCommunities;

    public AiUserMlClient(
            @Value("${ai-user-ml.base-url:http://100.115.252.61:8201}") String baseUrl,
            @Value("${ai-user-ml.api-token:aiuser-ml-api-token-dev-2026}") String apiToken,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.apiToken = apiToken;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    private <T> String postJson(String path, T body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
            String json = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            return restTemplate.postForEntity(baseUrl + path, entity, String.class).getBody();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CandidateItem {
        private String id;
        private String text;
        public CandidateItem(String id, String text) { this.id = id; this.text = text; }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RerankRequest {
        private String community;
        private String contentType;
        private List<CandidateItem> candidates;
        public RerankRequest(String community, String contentType, List<CandidateItem> candidates) {
            this.community = community; this.contentType = contentType; this.candidates = candidates;
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RankedItem {
        private String id;
        private double humanProb;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RerankResponse {
        private String winnerId;
        private List<RankedItem> ranked;
        private boolean degraded;
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IngestItem {
        private String community;
        private String contentType;
        private String text;
        private String label;
        /**
         * R3 (D-47): AI negative 출처 마커. label='ai' 항목은 'SELF_GENERATED' 필수.
         * ML /corpus/ingest가 이 필드 없는 ai 항목을 거부함으로써 재오염 차단.
         */
        private String source;
        public IngestItem(String community, String contentType, String text, String label) {
            this.community = community; this.contentType = contentType;
            this.text = text; this.label = label;
        }
        public IngestItem(String community, String contentType, String text, String label, String source) {
            this.community = community; this.contentType = contentType;
            this.text = text; this.label = label; this.source = source;
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IngestRequest {
        private List<IngestItem> items;
        public IngestRequest(List<IngestItem> items) { this.items = items; }
    }

    // ── Methods ───────────────────────────────────────────────────────────────

    /**
     * N개 초안 중 인간다움 점수 최고 winner 반환.
     * enabled=false 또는 WSL 다운 시 Optional.empty() → ActionExecutor가 첫 번째 초안으로 폴백.
     */
    public Optional<RerankResponse> rerank(String community, String contentType, List<CandidateItem> candidates) {
        if (!enabled || candidates == null || candidates.isEmpty()) return Optional.empty();
        try {
            String body = postJson("/rerank", new RerankRequest(community, contentType, candidates));
            if (body == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(body, RerankResponse.class));
        } catch (Exception e) {
            log.debug("AiUserMl rerank failed (non-critical, fallback to first draft): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 게시 완료 텍스트를 AI negative 코퍼스에 push (fire-and-forget).
     * collect=false 또는 WSL 다운 시 silent skip.
     *
     * R3 (D-47): source='SELF_GENERATED' 마커 전송.
     * ML /corpus/ingest는 label='ai'에 source 허용목록 검사 — 미마커 ai 항목 거부.
     */
    public void pushNegative(String community, String contentType, String text) {
        if (!collect || text == null || text.isBlank()) return;
        try {
            postJson("/corpus/ingest",
                new IngestRequest(List.of(new IngestItem(community, contentType, text, "ai", "SELF_GENERATED"))));
        } catch (Exception e) {
            log.debug("AiUserMl corpus ingest failed (non-critical): {}", e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
    public boolean isEnabledFor(String community) {
        if (!enabled) return false;
        Set<String> scopedCommunities = parseEnabledCommunities();
        if (scopedCommunities.isEmpty()) return true;
        if (community == null || community.isBlank()) return false;
        return scopedCommunities.contains(community.trim().toUpperCase());
    }
    public boolean isCollectEnabled() { return collect; }
    public int getBestOfN() { return bestOfN; }

    private Set<String> parseEnabledCommunities() {
        if (enabledCommunities == null || enabledCommunities.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : enabledCommunities.split(",")) {
            String item = raw == null ? "" : raw.trim().toUpperCase();
            if (!item.isEmpty()) {
                normalized.add(item);
            }
        }
        return normalized;
    }

    // ── Inventory Precompute ──────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AvailableCountResponse {
        private String source;
        private String category;
        private int count;
        private int windowDays;
        private String error;
    }

    /**
     * Query claimable inventory count for (source, category) pair.
     * Returns: count >= 0 on success, -1 on error/disabled (graceful degradation).
     *
     * Used by orchestrator to precompute empty (source, plaza) pairs
     * before entering nightly fill slot loop — avoids doomed claim attempts.
     * On error, returns -1 so the orchestrator assumes inventory exists (conservative).
     */
    public int getAvailableCount(String source, String category, int windowDays) {
        if (!enabled) return -1;
        try {
            StringBuilder url = new StringBuilder(baseUrl)
                    .append("/examples/available-count?source=").append(urlEncode(source));
            if (category != null && !category.isBlank()) {
                url.append("&category=").append(urlEncode(category));
            }
            url.append("&window_days=").append(windowDays);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            var response = restTemplate.exchange(url.toString(), org.springframework.http.HttpMethod.GET,
                    entity, AvailableCountResponse.class);

            if (response.getBody() == null) {
                return -1;
            }
            return response.getBody().count;
        } catch (Exception e) {
            log.debug("AiUserMl getAvailableCount failed (non-critical, assume non-empty): source={} category={} {}",
                    source, category, e.getMessage());
            return -1;  // Return -1 to indicate unknown/error; orchestrator treats as non-empty
        }
    }

    private String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }
}
