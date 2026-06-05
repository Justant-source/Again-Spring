package com.againspring.service.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    record SaveRequest(
        String content,
        String contentType,
        String category,
        String source,
        Double qualityScore
    ) {}
}
