package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.SkeletonExtractRequest;
import com.againspring.aiuser.llm.dto.SkeletonExtractResponse;
import com.againspring.aiuser.llm.exception.LlmCapacityException;
import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.service.SkeletonExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * persona-diversity-v4 WP2 — {@code POST /v2/extract-skeleton}.
 * 파싱 실패·필수 키 누락·sequence 부족은 400이 아니라 200 + {@code ok:false}로 반환한다
 * (문서 지시 — 오케스트레이터가 재시도/다음 소스로 넘어가는 판단을 응답 바디로 하게 한다).
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class SkeletonController {

    private final SkeletonExtractionService skeletonExtractionService;

    @PostMapping("/extract-skeleton")
    public ResponseEntity<SkeletonExtractResponse> extractSkeleton(@RequestBody SkeletonExtractRequest req) {
        String corrId = corrId(req == null ? null : req.getCorrelationId());
        try {
            SkeletonExtractResponse resp = skeletonExtractionService.extract(req, corrId);
            return ResponseEntity.ok(resp);
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(SkeletonExtractResponse.failure("CAPACITY: " + e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(SkeletonExtractResponse.failure("TIMEOUT"));
        } catch (Exception e) {
            log.error("skeleton extract error corr={} sourceExampleId={}", corrId,
                    req == null ? null : req.getSourceExampleId(), e);
            return ResponseEntity.ok(SkeletonExtractResponse.failure(e.getMessage()));
        }
    }

    private static String corrId(String provided) {
        return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString().substring(0, 8);
    }
}
