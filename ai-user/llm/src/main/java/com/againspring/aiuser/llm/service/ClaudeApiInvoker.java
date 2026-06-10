package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.exception.ClaudeCodeException;
import com.againspring.aiuser.llm.exception.LlmException;
import com.againspring.aiuser.llm.pool.CancelableInvocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
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
 * - API 키: ApiKeyProvider 경유 (DB 우선 → 환경변수 폴백)
 * - 시스템 프롬프트: <<<USER_PROMPT>>> 앞 부분
 * - 유저 메시지: <<<USER_PROMPT>>> 뒤 부분
 * - 프롬프트 캐싱: system 파트의 첫 번째 text block에 cache_control 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeApiInvoker implements Invoker {

    private static final String API_PATH       = "/v1/messages";
    private static final String API_VER        = "2023-06-01";
    // 프롬프트 캐싱 + 1시간 TTL 베타. AI 유저 tick은 10분 주기이고 jitter로 행동이 10~60분 창에 분산되므로
    // 기본 5분 TTL로는 틱 사이에 캐시가 항상 만료된다(히트율 0%). 1h TTL로 여러 틱을 하나의 캐시로 커버.
    private static final String CACHE_BETA     = "prompt-caching-2024-07-31,extended-cache-ttl-2025-04-11";
    private static final String CACHE_TTL      = "1h";
    private static final String SEP            = "<<<USER_PROMPT>>>";
    private static final String PERSONA_SEP    = "<<<PERSONA_SECTION>>>";
    private static final int    MAX_TOKENS     = 2048;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApiKeyProvider apiKeyProvider;

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
        String apiKey = apiKeyProvider.getKey();
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
            int personaIdx = systemPart.indexOf(PERSONA_SEP);
            if (promptCaching && personaIdx >= 0) {
                // Block 1: 정적 규칙 섹션 — cache_control 적용 (캐시 히트 대상)
                String staticPart  = systemPart.substring(0, personaIdx).trim();
                String dynamicPart = systemPart.substring(personaIdx + PERSONA_SEP.length()).trim();
                ObjectNode block1 = systemArr.addObject();
                block1.put("type", "text");
                block1.put("text", staticPart);
                block1.putObject("cache_control").put("type", "ephemeral").put("ttl", CACHE_TTL);
                // Block 2: 페르소나별 섹션 — cache_control 없음 (호출마다 다름)
                if (!dynamicPart.isBlank()) {
                    ObjectNode block2 = systemArr.addObject();
                    block2.put("type", "text");
                    block2.put("text", dynamicPart);
                }
            } else {
                // 구분자 없거나 캐싱 비활성 — 단일 블록
                ObjectNode textBlock = systemArr.addObject();
                textBlock.put("type", "text");
                textBlock.put("text", systemPart.replace(PERSONA_SEP, "").trim());
                if (promptCaching) {
                    textBlock.putObject("cache_control").put("type", "ephemeral").put("ttl", CACHE_TTL);
                }
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

            String baseUrl = apiKeyProvider.getBaseUrl().replaceAll("/+$", "");
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + API_PATH))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VER)
                .header("content-type", "application/json");
            if (promptCaching) {
                reqBuilder.header("anthropic-beta", CACHE_BETA);
            }
            HttpRequest req = reqBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 529 || res.statusCode() == 529) {
                throw new ClaudeCodeException("THROTTLED", "Anthropic API overloaded (529)", 529, null);
            }
            if (res.statusCode() != 200) {
                // 크레딧/쿼터 소진 등 모든 비정상 응답 → ERROR 로그 + 예외 (절대 콘텐츠로 게시 안 됨)
                log.error("Anthropic API error {} — generation failed, NOT publishing: {}",
                    res.statusCode(), res.body());
                throw new ClaudeCodeException("API_ERROR", "API status " + res.statusCode(), res.statusCode(), null);
            }

            JsonNode resp = MAPPER.readTree(res.body());

            // 토큰 사용량 로깅
            JsonNode usage = resp.get("usage");
            if (usage != null) {
                int inTok    = usage.path("input_tokens").asInt();
                int cacheRead = usage.path("cache_read_input_tokens").asInt(0);
                int cacheWrite = usage.path("cache_creation_input_tokens").asInt(0);
                long denom = (long) inTok + cacheRead + cacheWrite;
                int hitPct = denom > 0 ? (int) Math.round(cacheRead * 100.0 / denom) : 0;
                log.info("API usage: input={} output={} cache_read={} cache_write={} cache_hit={}%",
                    inTok, usage.path("output_tokens").asInt(), cacheRead, cacheWrite, hitPct);
            }

            JsonNode contentArr = resp.path("content");
            if (contentArr.isArray() && contentArr.size() > 0) {
                String text = contentArr.get(0).path("text").asText("");
                // 방어: 제공자 오류 문자열이 본문에 섞여 나오면 실패 처리 (게시 차단)
                if (LlmErrorSignature.looksLikeProviderError(text)) {
                    log.error("API output looks like a provider error — refusing to return as content");
                    throw new ClaudeCodeException("PROVIDER_ERROR", "Provider error text in API output", -1, null);
                }
                return text;
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
