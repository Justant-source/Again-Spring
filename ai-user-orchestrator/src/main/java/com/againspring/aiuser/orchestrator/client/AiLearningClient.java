package com.againspring.aiuser.orchestrator.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * AI Learning 서비스 클라이언트.
 * dev/prod 공용 단일 서비스(포트 8099)와 통신.
 * enabled=false 시 모든 호출 silent skip.
 */
@Slf4j
@Component
public class AiLearningClient {

    private final RestClient restClient;

    @Value("${ai-learning.enabled:false}")
    private boolean enabled;

    public AiLearningClient(@Value("${ai-learning.base-url:http://againspring-ai-learning:8099}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
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
        public SearchRequest(String query, String contentType, String category, int topK) {
            this.query = query; this.contentType = contentType;
            this.category = category; this.topK = topK;
        }
    }

    @Getter @Setter @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExampleItem {
        private Long id;
        private String content;
        private String source;
        private Double score;
    }

    /** 합격한 생성 텍스트를 예시 뱅크에 저장 (비동기, 실패 silent) */
    public void saveAsync(String content, String contentType, String category, String source) {
        if (!enabled || content == null || content.isBlank()) return;
        try {
            restClient.post()
                .uri("/examples/save")
                .body(new SaveRequest(content, contentType, category, source, null))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.debug("AiLearning save failed (non-critical): {}", e.getMessage());
        }
    }

    /** 유사 예시 top-K 검색. 실패 시 빈 리스트 반환 */
    public List<ExampleItem> findSimilar(String query, String contentType, String category, int topK) {
        if (!enabled || query == null || query.isBlank()) return Collections.emptyList();
        try {
            List<ExampleItem> result = restClient.post()
                .uri("/examples/search")
                .body(new SearchRequest(query, contentType, category, topK))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExampleItem>>() {});
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.debug("AiLearning search failed (non-critical): {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 크롤링 트리거 */
    public void triggerCrawl(String source, int limit) {
        if (!enabled) return;
        try {
            restClient.post()
                .uri("/crawl/{source}?limit={limit}", source, limit)
                .retrieve()
                .toBodilessEntity();
            log.info("Crawl triggered: source={} limit={}", source, limit);
        } catch (Exception e) {
            log.debug("Crawl trigger failed: {}", e.getMessage());
        }
    }
}
