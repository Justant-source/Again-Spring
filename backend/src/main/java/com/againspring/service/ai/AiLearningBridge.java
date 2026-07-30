package com.againspring.service.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * backend → ai-user/learning 서비스 브릿지.
 * orchestrator AiLearningClient의 saveAsync 패턴을 backend에서 재현.
 * enabled=false 시 모든 호출 silent skip.
 *
 * 첨삭본(source="ADMIN_CORRECTED", quality_score=1.0)을 example_bank에 저장해
 * 이후 AI 유저 RAG 검색에서 상위 노출 → dynamicExamples 자동 주입.
 */
@Slf4j
@Component
public class AiLearningBridge {

    private final RestClient restClient;

    @Value("${ai-learning.enabled:false}")
    private boolean enabled;

    public AiLearningBridge(@Value("${ai-learning.base-url:http://againspring-ai-learning:8099}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 첨삭 완료 텍스트를 example_bank에 저장.
     * 실패해도 첨삭 처리 자체에 영향 없음 (silent).
     *
     * @param content      첨삭된 최종 텍스트
     * @param contentType  "POST" | "COMMENT"
     * @param category     글 카테고리 (nullable)
     * @param qualityScore 첨삭본 품질 (1.0 고정)
     */
    public void saveCorrectedAsync(String content, String contentType, String category, double qualityScore) {
        if (!enabled || content == null || content.isBlank()) return;
        try {
            restClient.post()
                .uri("/examples/save")
                .body(new SaveRequest(content, contentType, category, "ADMIN_CORRECTED", qualityScore))
                .retrieve()
                .toBodilessEntity();
            log.debug("[ai-learning-bridge] saved corrected example type={} category={}", contentType, category);
        } catch (Exception e) {
            log.debug("[ai-learning-bridge] save failed (non-critical): {}", e.getMessage());
        }
    }

    /**
     * 크롤 로그 조회 — ai-user/learning의 GET /crawl/log 엔드포인트 호출.
     * 최근 50건을 조회해 24시간 내 성공/실패 통계를 반환.
     *
     * 실패 시 (연결 오류, 타임아웃 등):
     *  - 원본 예외를 ERROR 로그로 남김
     *  - 호출부에 명확한 실패 신호 (CrawlLog.error(...))를 반환
     *
     * @return 최근 크롤 로그 목록 (50건 한도), 또는 조회 실패 시 오류 상태 로그
     */
    public List<CrawlLog> getCrawlLogsWithFallback() {
        try {
            var response = restClient.get()
                .uri("/crawl/log")
                .retrieve()
                .toEntity(CrawlLog[].class);
            if (response.getBody() != null) {
                log.debug("[ai-learning-bridge] fetched {} crawl logs", response.getBody().length);
                return List.of(response.getBody());
            }
        } catch (Exception e) {
            log.error("[ai-learning-bridge] failed to fetch crawl logs: {}", e.getMessage(), e);
        }
        return List.of(CrawlLog.error("Crawl log query failed"));
    }

    /**
     * AI Learning 서비스의 크롤 로그 레코드 (ai-user/learning/app/api/crawl.py 응답 포맷).
     * @param source "natepan", "blind", "theqoo", "clien" 등
     * @param status "SUCCESS" | "FAILED"
     * @param saved 저장된 항목 수
     * @param at 크롤 실행 시각 (ISO-8601 UTC)
     */
    public record CrawlLog(
        String source,
        String status,
        @JsonProperty("saved")
        Integer itemsSaved,
        String at
    ) {
        /**
         * 오류 상태 로그 생성.
         */
        public static CrawlLog error(String message) {
            return new CrawlLog("ERROR", "FAILED", 0, message);
        }
    }

    record SaveRequest(
        String content,
        String contentType,
        String category,
        String source,
        Double qualityScore
    ) {}
}
