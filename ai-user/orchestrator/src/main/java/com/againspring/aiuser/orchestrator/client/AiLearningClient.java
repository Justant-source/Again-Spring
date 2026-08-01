package com.againspring.aiuser.orchestrator.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI Learning 서비스 클라이언트.
 * dev/prod 공용 단일 서비스(포트 8099)와 통신.
 * enabled=false 시 모든 호출 silent skip.
 */
@Slf4j
@Component
public class AiLearningClient {

    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai-learning.enabled:false}")
    private boolean enabled;

    public AiLearningClient(
            @Value("${ai-learning.base-url:http://againspring-ai-learning:8099}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    private <T> ResponseEntity<String> postJson(String path, T body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String json = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(json, headers);
            return restTemplate.postForEntity(baseUrl + path, entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveRequest {
        private String content;
        private String contentType;
        private String category;
        private String source;
        private Double qualityScore;
        public SaveRequest(String content, String contentType, String category, String source, Double qualityScore) {
            this.content = content; this.contentType = contentType;
            this.category = category; this.source = source; this.qualityScore = qualityScore;
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchRequest {
        private String query;
        private String contentType;
        private String category;
        private int topK;
        private String register;             // "casual" | "polite" | "mixed" | null
        private boolean excludeSelfGenerated; // true이면 SELF_GENERATED 제외
        public SearchRequest(String query, String contentType, String category, int topK) {
            this.query = query; this.contentType = contentType;
            this.category = category; this.topK = topK;
            this.register = null;
            this.excludeSelfGenerated = false;
        }
        public SearchRequest(String query, String contentType, String category, int topK, String register) {
            this.query = query; this.contentType = contentType;
            this.category = category; this.topK = topK;
            this.register = register;
            this.excludeSelfGenerated = true;
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StyleSampleRequest {
        private String contentType;   // "POST" | "COMMENT"
        private String source;        // 크롤 소스 (natepan 등). null=전체 크롤 소스
        private String register;      // "casual" | "polite" | null
        private int topK;
        private int maxLen;           // 본문 최대 길이 (자)
        public StyleSampleRequest(String contentType, String source, String register, int topK, int maxLen) {
            this.contentType = contentType; this.source = source;
            this.register = register; this.topK = topK; this.maxLen = maxLen;
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExampleItem {
        private Long id;
        private String content;
        private String source;
        private Double score;
        /** 원본 비교 기능: 크롤 원본 제목 (신규 크롤부터, 기존 행은 null) */
        private String title;
        /** 원본 비교 기능: 크롤 원본 URL */
        @JsonProperty("source_url")
        private String sourceUrl;

        /** 이 항목이 단일 원본 재구성 소스로 사용 가능한지 — source_url 보유 여부로 판단 */
        public boolean hasSourceProvenance() {
            return sourceUrl != null && !sourceUrl.isBlank();
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyTopicItem {
        private Long id;
        private String category;
        private String text;
        private Integer usedCount;
        private Double qualityScore;
    }

    /** 합격한 생성 텍스트를 예시 뱅크에 저장 (비동기, 실패 silent) */
    public void saveAsync(String content, String contentType, String category, String source) {
        if (!enabled || content == null || content.isBlank()) return;
        try {
            postJson("/examples/save", new SaveRequest(content, contentType, category, source, null));
        } catch (Exception e) {
            log.debug("AiLearning save failed (non-critical): {}", e.getMessage());
        }
    }

    /** 유사 예시 top-K 검색. 실패 시 빈 리스트 반환 */
    public List<ExampleItem> findSimilar(String query, String contentType, String category, int topK) {
        return findSimilar(query, contentType, category, topK, null);
    }

    /** 유사 예시 top-K 검색 (register/excludeSelfGenerated 포함). 실패 시 빈 리스트 반환 */
    public List<ExampleItem> findSimilar(String query, String contentType, String category, int topK, String register) {
        if (!enabled || query == null || query.isBlank()) return Collections.emptyList();
        try {
            ResponseEntity<String> resp = postJson("/examples/search",
                new SearchRequest(query, contentType, category, topK, register));
            if (resp.getBody() == null) return Collections.emptyList();
            List<ExampleItem> result = objectMapper.readValue(resp.getBody(),
                new TypeReference<List<ExampleItem>>() {});
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.debug("AiLearning search failed (non-critical): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 문체 앵커용 랜덤 샘플 — 주제 무관, 소스(voice)·레지스터·타입만 일치 (문체 현실화 S2).
     * 호출마다 다른 예시가 반환됨 (learning 쪽 ORDER BY RAND()). 실패 시 빈 리스트.
     */
    public List<ExampleItem> styleSample(String source, String contentType, String register, int topK, int maxLen) {
        if (!enabled) return Collections.emptyList();
        try {
            ResponseEntity<String> resp = postJson("/examples/style-sample",
                new StyleSampleRequest(contentType, source, register, topK, maxLen));
            if (resp.getBody() == null) return Collections.emptyList();
            List<ExampleItem> result = objectMapper.readValue(resp.getBody(),
                new TypeReference<List<ExampleItem>>() {});
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.debug("AiLearning styleSample failed (non-critical): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 오늘의 갈등 주제 시드 조회 (least-used 우선). 실패 시 빈 리스트 반환 */
    public List<DailyTopicItem> fetchDailyTopics(String category, int limit) {
        if (!enabled || category == null || category.isBlank()) return Collections.emptyList();
        try {
            String url = baseUrl + "/topics/today?category=" + category + "&limit=" + limit;
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) return Collections.emptyList();
            List<DailyTopicItem> result = objectMapper.readValue(body,
                new TypeReference<List<DailyTopicItem>>() {});
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.debug("AiLearning fetchDailyTopics failed (non-critical): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 토픽 사용 카운트 +1 (fire-and-forget, 실패 silent) */
    public void markTopicUsed(Long topicId) {
        if (!enabled || topicId == null) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.postForEntity(baseUrl + "/topics/" + topicId + "/use", entity, Void.class);
        } catch (Exception e) {
            log.debug("AiLearning markTopicUsed failed (non-critical): {}", e.getMessage());
        }
    }

    /** 크롤링 트리거 */
    public void triggerCrawl(String source, int limit) {
        if (!enabled) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.postForEntity(baseUrl + "/crawl/" + source + "?limit=" + limit, entity, Void.class);
            log.info("Crawl triggered: source={} limit={}", source, limit);
        } catch (Exception e) {
            log.debug("Crawl trigger failed: {}", e.getMessage());
        }
    }

    /**
     * KURE-v1 embedding via learning {@code POST /embed}. Input truncated to 512 chars
     * (server also truncates). On any failure returns empty — never throws (degrade path).
     */
    public List<Double> embed(String text) {
        return embedOptional(text).orElse(Collections.emptyList());
    }

    /** Same as {@link #embed} but distinguishes "disabled/blank" from "call failed". */
    public Optional<List<Double>> embedOptional(String text) {
        if (!enabled || text == null || text.isBlank()) return Optional.empty();
        try {
            String clipped = text.length() > 512 ? text.substring(0, 512) : text;
            ResponseEntity<String> resp = postJson("/embed", Map.of("text", clipped));
            if (resp.getBody() == null || resp.getBody().isBlank()) return Optional.empty();
            EmbedResponse parsed = objectMapper.readValue(resp.getBody(), EmbedResponse.class);
            if (parsed == null || parsed.embedding == null || parsed.embedding.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(parsed.embedding);
        } catch (Exception e) {
            log.debug("AiLearning embed failed (non-critical): {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbedResponse {
        private List<Double> embedding;
    }
}
