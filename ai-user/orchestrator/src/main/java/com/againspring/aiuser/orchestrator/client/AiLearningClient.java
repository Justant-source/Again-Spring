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
        return findSimilar(query, contentType, category, topK, null);
    }

    /** 유사 예시 top-K 검색 (register/excludeSelfGenerated 포함). 실패 시 빈 리스트 반환 */
    public List<ExampleItem> findSimilar(String query, String contentType, String category, int topK, String register) {
        if (!enabled || query == null || query.isBlank()) return Collections.emptyList();
        try {
            List<ExampleItem> result = restClient.post()
                .uri("/examples/search")
                .body(new SearchRequest(query, contentType, category, topK, register))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExampleItem>>() {});
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
            List<ExampleItem> result = restClient.post()
                .uri("/examples/style-sample")
                .body(new StyleSampleRequest(contentType, source, register, topK, maxLen))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ExampleItem>>() {});
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
            List<DailyTopicItem> result = restClient.get()
                .uri("/topics/today?category={category}&limit={limit}", category, limit)
                .retrieve()
                .body(new ParameterizedTypeReference<List<DailyTopicItem>>() {});
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
            restClient.post()
                .uri("/topics/{id}/use", topicId)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.debug("AiLearning markTopicUsed failed (non-critical): {}", e.getMessage());
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
