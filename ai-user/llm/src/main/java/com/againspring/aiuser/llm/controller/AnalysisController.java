package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.GenResponse;
import com.againspring.aiuser.llm.dto.PostAnalysisRequest;
import com.againspring.aiuser.llm.exception.LlmCapacityException;
import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.againspring.aiuser.llm.service.PromptAssembler;
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
 * 글 분석 엔드포인트 — 좋아요·투표 결정용 구조화 신호 추출.
 * 생성(/generate)과 분리: 거대한 생성 시스템 프롬프트가 아닌 최소 프롬프트 사용.
 * 응답은 LLM 원문(JSON 문자열) 그대로 — orchestrator가 파싱·캐시한다 (/generate/persona 패턴).
 */
@Slf4j
@RestController
@RequestMapping("/analyze")
@RequiredArgsConstructor
public class AnalysisController {

    private final LlmWorkerPool pool;
    private final PromptAssembler promptAssembler;

    @PostMapping("/post")
    public ResponseEntity<GenResponse> analyzePost(@RequestBody PostAnalysisRequest req) {
        String corrId = (req.getCorrelationId() != null && !req.getCorrelationId().isBlank())
            ? req.getCorrelationId() : UUID.randomUUID().toString().substring(0, 8);
        long start = System.currentTimeMillis();
        try {
            String prompt = promptAssembler.assemblePostAnalysisPrompt(req);
            if (prompt.isBlank()) {
                return ResponseEntity.badRequest().body(GenResponse.genError("empty prompt"));
            }
            // 30s timeout — 분석은 짧은 출력. 4-arg = 기본 backend (CLI), API 달러 비용 없음.
            String raw = pool.executeSyncTask(prompt, null, 30_000L, corrId);
            String text = raw != null ? raw.trim() : "";
            return ResponseEntity.ok(GenResponse.success(text, System.currentTimeMillis() - start, corrId));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(GenResponse.capacity(e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(GenResponse.timeout(corrId));
        } catch (Exception e) {
            log.error("Post analysis error: corr={}", corrId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(GenResponse.genError(e.getMessage()));
        }
    }
}
