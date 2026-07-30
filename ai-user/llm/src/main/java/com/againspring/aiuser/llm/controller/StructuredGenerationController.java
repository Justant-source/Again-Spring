package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.HumanReplyBatchRequest;
import com.againspring.aiuser.llm.dto.HumanReplyBatchResponse;
import com.againspring.aiuser.llm.dto.ThreadPlanRequest;
import com.againspring.aiuser.llm.dto.ThreadPlanResponse;
import com.againspring.aiuser.llm.exception.LlmCapacityException;
import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.service.StructuredGenerationException;
import com.againspring.aiuser.llm.service.StructuredGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** New plan-only contracts. These endpoints never accept the legacy API backend selector. */
@Slf4j
@RestController
@RequestMapping("/v2/generate")
@RequiredArgsConstructor
public class StructuredGenerationController {
    private final StructuredGenerationService structuredGeneration;

    @PostMapping("/thread-plan")
    public ResponseEntity<?> threadPlan(@RequestBody ThreadPlanRequest request) {
        String corr = correlation(request == null ? null : request.getCorrelationId());
        try {
            return ResponseEntity.ok(structuredGeneration.createThreadPlan(request, corr));
        } catch (IllegalArgumentException | StructuredGenerationException e) {
            return ResponseEntity.badRequest().body(error("INVALID_STRUCTURED_REQUEST", e.getMessage(), corr));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error("CAPACITY", e.getMessage(), corr));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error("TIMEOUT", e.getMessage(), corr));
        } catch (Exception e) {
            log.error("Thread-plan generation failed: corr={}", corr, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("GENERATION_FAILED", "generation failed", corr));
        }
    }

    @PostMapping("/human-replies")
    public ResponseEntity<?> humanReplies(@RequestBody HumanReplyBatchRequest request) {
        String corr = correlation(request == null ? null : request.getCorrelationId());
        try {
            return ResponseEntity.ok(structuredGeneration.createHumanReplies(request, corr));
        } catch (IllegalArgumentException | StructuredGenerationException e) {
            return ResponseEntity.badRequest().body(error("INVALID_STRUCTURED_REQUEST", e.getMessage(), corr));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error("CAPACITY", e.getMessage(), corr));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error("TIMEOUT", e.getMessage(), corr));
        } catch (Exception e) {
            log.error("Human-reply generation failed: corr={}", corr, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("GENERATION_FAILED", "generation failed", corr));
        }
    }

    private static String correlation(String supplied) { return supplied == null || supplied.isBlank() ? UUID.randomUUID().toString().substring(0, 8) : supplied; }
    private static Map<String, String> error(String code, String message, String corr) { return Map.of("errorCode", code, "message", message == null ? "" : message, "correlationId", corr); }
}
