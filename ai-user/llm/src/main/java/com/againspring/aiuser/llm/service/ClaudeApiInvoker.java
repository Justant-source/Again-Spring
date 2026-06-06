package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.ClaudeCodeException;
import com.againspring.aiuser.llm.exception.LlmException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Anthropic Messages API를 직접 호출하는 LLM 인보커.
 * - ANTHROPIC_API_KEY 환경변수 필요
 * - 시스템 프롬프트: <<<USER_PROMPT>>> 앞 부분
 * - 유저 메시지: <<<USER_PROMPT>>> 뒤 부분
 * - 프롬프트 캐싱: system 파트의 첫 번째 text block에 cache_control 적용
 */
@Slf4j
@Service
public class ClaudeApiInvoker implements Invoker {

    private static final String API_URL  = "https://api.anthropic.com/v1/messages";
    private static final String API_VER  = "2023-06-01";
    private static final String SEP      = "<<<USER_PROMPT>>>";
    private static final int    MAX_TOKENS = 2048;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${anthropic.api-key:}")
    private String apiKey;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}")
    private String defaultModel;

    @Value("${llm.api.prompt-caching:true}")
    private boolean promptCaching;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    @Override
    public String invoke(String prompt, String model) throws LlmException {
        return call(prompt, model, 120_000);
    }

    @Override
    public String invokeWithCancelSupport(String prompt, String model, CancelableInvocation inv) throws Exception {
        // API 경로는 취소가 없음 — 동기 호출로 처리
        if (inv.isCanceled()) throw new com.againspring.aiuser.llm.exception.InvocationCanceledException("Pre-cancel", inv.getInvocationId());
        return call(prompt, model, 120_000);
    }

    private String call(String prompt, String model, long timeoutMs) throws LlmException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ClaudeCodeException("API_KEY_MISSING", "ANTHROPIC_API_KEY not configured", -1, null);
        }

        String resolvedModel = (model != null && !model.isBlank()) ? model : defaultModel;

        // ── 시스템 / 유저 분리 ────────────────────────────────────────────
        String systemPart = "";
        String userPart   = prompt;
        int sepIdx = prompt.indexOf(SEP);
        if (sepIdx >= 0) {
            systemPart = prompt.substring(0, sepIdx).trim();
            userPart   = prompt.substring(sepIdx + SEP.length()).trim();
        }

        // ── 요청 JSON 구성 ─────────────────────────────────────────────────
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", resolvedModel);
        body.put("max_tokens", MAX_TOKENS);

        if (!systemPart.isBlank()) {
            ArrayNode systemArr = body.putArray("system");
            ObjectNode textBlock = systemArr.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", systemPart);
            if (promptCaching) {
                // 시스템 프롬프트 캐싱 (입력 토큰 ~76.5% 절감)
                ObjectNode cacheCtrl = textBlock.putObject("cache_control");
                cacheCtrl.put("type", "ephemeral");
            }
        }

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ArrayNode content = userMsg.putArray("content");
        ObjectNode textNode = content.addObject();
        textNode.put("type", "text");
        textNode.put("text", userPart);

        try {
            String requestBody = MAPPER.writeValueAsString(body);

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VER)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 529 || res.statusCode() == 529) {
                throw new ClaudeCodeException("THROTTLED", "Anthropic API overloaded (529)", 529, null);
            }
            if (res.statusCode() != 200) {
                log.warn("Anthropic API error {}: {}", res.statusCode(), res.body());
                throw new ClaudeCodeException("API_ERROR", "API status " + res.statusCode(), res.statusCode(), null);
            }

            JsonNode resp = MAPPER.readTree(res.body());

            // 토큰 사용량 로깅
            JsonNode usage = resp.get("usage");
            if (usage != null) {
                log.debug("API usage: input={} output={} cache_read={} cache_write={}",
                    usage.path("input_tokens").asInt(),
                    usage.path("output_tokens").asInt(),
                    usage.path("cache_read_input_tokens").asInt(0),
                    usage.path("cache_creation_input_tokens").asInt(0));
            }

            JsonNode contentArr = resp.path("content");
            if (contentArr.isArray() && contentArr.size() > 0) {
                return contentArr.get(0).path("text").asText("");
            }
            return "";

        } catch (ClaudeCodeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            throw new ClaudeCodeException("API_CALL_FAILED", e.getMessage(), -1, null);
        }
    }
}
