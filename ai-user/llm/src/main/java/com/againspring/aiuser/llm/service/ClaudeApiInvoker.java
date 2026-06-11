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
 * - 시스템 프롬프트: <<<USER_PROMPT>>> 앞 부분 → &lt;instructions&gt; 태그로 user 메시지에 주입
 * - 유저 메시지: <<<USER_PROMPT>>> 뒤 부분
 *
 * clcocloud 프록시는 system 필드 포함 요청을 다른 모델로 라우팅하므로 system 필드 미사용.
 *
 * 프롬프트 캐싱 (2026-06-11 복원, 캐싱 P1):
 * - user content를 2블록으로 분리 — block1=&lt;instructions&gt;+정적 prefix(PERSONA_SECTION 앞)에
 *   cache_control, block2=페르소나 섹션+유저 요청. system 필드 없이 캐싱 (Kiro 라우팅 버그 회피 유지).
 * - 기본 TTL 5m (GA — beta 헤더 불필요). ⚠️ 1h TTL의 anthropic-beta 헤더는 clcocloud에서
 *   Kiro 오라우팅을 유발함이 프로브로 확인됨(2026-06-11) — LLM_API_CACHE_TTL=1h는 직접 API 전환 시에만.
 * - Haiku 4.5 최소 캐시 prefix 4096토큰 — 미달 시 조용히 스킵되므로 길이 WARN 가드 포함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeApiInvoker implements Invoker {

    private static final String API_PATH       = "/v1/messages";
    private static final String API_VER        = "2023-06-01";
    private static final String SEP            = "<<<USER_PROMPT>>>";
    private static final String PERSONA_SEP    = "<<<PERSONA_SECTION>>>";
    private static final int    MAX_TOKENS     = 2048;
    /** 1h TTL 베타 — 기본 미사용 (clcocloud Kiro 오라우팅 유발). */
    private static final String CACHE_BETA_1H  = "extended-cache-ttl-2025-04-11";
    /** Haiku 4096토큰 ≈ 4,730자 — 이보다 짧으면 캐싱이 조용히 스킵됨 (여유 포함 경고 임계). */
    private static final int    CACHE_MIN_PREFIX_CHARS = 4800;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApiKeyProvider apiKeyProvider;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}")
    private String defaultModel;

    @Value("${llm.api.prompt-caching:true}")
    private boolean promptCaching;

    /** "5m"(기본, GA) | "1h"(beta 헤더 필요 — 직접 Anthropic API에서만 사용할 것). */
    @Value("${llm.api.cache-ttl:5m}")
    private String cacheTtl;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    @Override
    public String invoke(String prompt, String model) throws LlmException {
        return call(prompt, model, 120_000);
    }

    /**
     * 요청 body 구성 (테스트 가능하도록 분리).
     *
     * clcocloud 프록시는 system 필드 포함 요청을 다른 모델로 라우팅하므로 system 필드 미사용 —
     * system 내용을 user 메시지에 &lt;instructions&gt; 태그로 주입.
     *
     * 캐싱 활성 + PERSONA_SECTION 분리자 존재 시 user content를 2블록으로:
     *   block1: "&lt;instructions&gt;\n" + 정적 prefix  ← cache_control (모든 페르소나·호출 공통)
     *   block2: 페르소나 섹션 + "&lt;/instructions&gt;" + 유저 요청  ← 호출마다 가변
     * 두 블록을 이으면 기존 단일 블록과 의미상 동일한 프롬프트 — 모델 동작 불변.
     */
    ObjectNode buildRequestBody(String systemPart, String userPart, String resolvedModel) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", resolvedModel);
        body.put("max_tokens", MAX_TOKENS);

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        ArrayNode content = userMsg.putArray("content");

        int personaIdx = systemPart != null ? systemPart.indexOf(PERSONA_SEP) : -1;
        if (promptCaching && personaIdx >= 0) {
            String staticPart  = systemPart.substring(0, personaIdx).trim();
            String dynamicPart = systemPart.substring(personaIdx + PERSONA_SEP.length()).trim();
            if (staticPart.length() < CACHE_MIN_PREFIX_CHARS) {
                // Haiku 최소 4096토큰 미달이면 cache_control이 있어도 조용히 스킵됨 — 가이드 축소 시 감지용
                log.warn("cache prefix {} chars < {} — 4096토큰 최소치 미달로 캐싱이 스킵될 수 있음 "
                    + "(voice 가이드 축소 주의)", staticPart.length(), CACHE_MIN_PREFIX_CHARS);
            }
            ObjectNode block1 = content.addObject();
            block1.put("type", "text");
            block1.put("text", "<instructions>\n" + staticPart);
            ObjectNode cc = block1.putObject("cache_control");
            cc.put("type", "ephemeral");
            if ("1h".equals(cacheTtl)) {
                cc.put("ttl", "1h");
            }
            ObjectNode block2 = content.addObject();
            block2.put("type", "text");
            block2.put("text", "\n" + dynamicPart + "\n</instructions>\n\n" + userPart);
        } else {
            // 캐싱 비활성 또는 분리자 없음 — 기존 단일 블록
            String cleanSystem = systemPart != null ? systemPart.replace(PERSONA_SEP, "").trim() : "";
            String fullUserText = cleanSystem.isBlank()
                ? userPart
                : "<instructions>\n" + cleanSystem + "\n</instructions>\n\n" + userPart;
            ObjectNode textNode = content.addObject();
            textNode.put("type", "text");
            textNode.put("text", fullUserText);
        }
        return body;
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
        ObjectNode body = buildRequestBody(systemPart, userPart, resolvedModel);

        try {
            String requestBody = MAPPER.writeValueAsString(body);

            String baseUrl = apiKeyProvider.getBaseUrl().replaceAll("/+$", "");
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + API_PATH))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VER)
                .header("content-type", "application/json");
            // ⚠️ beta 헤더는 1h TTL 명시 설정 시에만 — clcocloud에서 Kiro 오라우팅 유발 (2026-06-11 프로브)
            if (promptCaching && "1h".equals(cacheTtl)) {
                reqBuilder.header("anthropic-beta", CACHE_BETA_1H);
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

            // 토큰 사용량 로깅 (model=응답이 실제 처리된 모델 — POST 전용 Sonnet 승격 검증용)
            JsonNode usage = resp.get("usage");
            if (usage != null) {
                int inTok    = usage.path("input_tokens").asInt();
                int cacheRead = usage.path("cache_read_input_tokens").asInt(0);
                int cacheWrite = usage.path("cache_creation_input_tokens").asInt(0);
                long denom = (long) inTok + cacheRead + cacheWrite;
                int hitPct = denom > 0 ? (int) Math.round(cacheRead * 100.0 / denom) : 0;
                // stop= max_tokens면 길이 제한 절단, end_turn이면 모델 자연 종료 (글 짧음 원인 진단용)
                log.info("API usage: model={} stop={} input={} output={} cache_read={} cache_write={} cache_hit={}%",
                    resp.path("model").asText(resolvedModel), resp.path("stop_reason").asText("?"),
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
