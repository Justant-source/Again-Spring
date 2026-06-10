package com.againspring.llmworker.controller;

import com.againspring.llmworker.service.LlmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 내부 설정 업데이트 엔드포인트 — admin backend에서만 호출.
 */
@Slf4j
@RestController
@RequestMapping("/internal/config")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigService llmConfigService;

    /**
     * Anthropic API base URL 핫-업데이트.
     * body: {"baseUrl": "https://api.clcocloud.com/claude/v1"} (빈 문자열이면 기본값으로 초기화)
     */
    @PostMapping("/anthropic-base-url")
    public ResponseEntity<Map<String, String>> updateAnthropicBaseUrl(
            @RequestBody Map<String, String> body) {
        String url = body.get("baseUrl");
        llmConfigService.setAnthropicBaseUrl(url);
        String current = llmConfigService.getAnthropicBaseUrl();
        return ResponseEntity.ok(Map.of(
                "status", "updated",
                "baseUrl", current != null ? current : ""
        ));
    }

    @GetMapping("/anthropic-base-url")
    public ResponseEntity<Map<String, String>> getAnthropicBaseUrl() {
        String current = llmConfigService.getAnthropicBaseUrl();
        return ResponseEntity.ok(Map.of(
                "baseUrl", current != null ? current : ""
        ));
    }
}
