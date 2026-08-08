package com.againspring.aiuser.orchestrator.client;

import com.againspring.aiuser.orchestrator.client.dto.GenDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class LlmAiUserClient {

    private final RestClient restClient;

    public LlmAiUserClient(@Qualifier("llmAiUserRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /** 피기백 반응을 포함한 생성 결과. */
    public record GenResult(String text, String reactionsJson) {}

    public Optional<String> generatePost(GenDto.PostRequest req) {
        return generate("/generate/post", req);
    }

    public Optional<String> generateComment(GenDto.CommentRequest req) {
        return generate("/generate/comment", req);
    }

    public Optional<String> generateReply(GenDto.ReplyRequest req) {
        return generate("/generate/reply", req);
    }

    /** Plan-first contract. Callers persist its output before any publish attempt. */
    public Optional<java.util.Map<String, Object>> generateThreadPlan(java.util.Map<String, Object> request) {
        return generateStructured("/v2/generate/thread-plan", request);
    }

    /**
     * Paired Call1 ({@code PAIRED_PHASE1}): author post + phase1 comments.
     * Orchestrator scheduling/hold owns when this is invoked.
     */
    public Optional<java.util.Map<String, Object>> generatePairedCall1(java.util.Map<String, Object> request) {
        return generateStructured("/v2/generate/paired-phase1", request);
    }

    /**
     * Paired Call2 ({@code PAIRED_PHASE2}): partner body + phase2 comments.
     * Request should include author body and up to 5–8 published top-level comments.
     */
    public Optional<java.util.Map<String, Object>> generatePairedCall2(java.util.Map<String, Object> request) {
        return generateStructured("/v2/generate/paired-phase2", request);
    }

    /** One bounded request for the 30-minute human-comment response batch. */
    public Optional<java.util.Map<String, Object>> generateHumanReplies(java.util.Map<String, Object> request) {
        return generateStructured("/v2/generate/human-replies", request);
    }

    private Optional<java.util.Map<String, Object>> generateStructured(String path, Object request) {
        String correlationId = extractCorrelationId(request);
        int maxAttempts = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> response = restClient.post().uri(path).body(request).retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});
                if (response != null && !response.containsKey("errorCode")) {
                    if (attempt > 1) {
                        log.info("[{}] Structured generation succeeded on attempt {}/{} for {}",
                                 correlationId, attempt, maxAttempts, path);
                    }
                    return Optional.of(response);
                }

                String errorCode = response == null ? "empty" : (String) response.get("errorCode");
                log.warn("[{}] Attempt {}/{}: Structured generation failed on {}: {}",
                         correlationId, attempt, maxAttempts, path, errorCode);

                if (attempt < maxAttempts) {
                    Thread.sleep(delayMs);
                    delayMs *= 2; // exponential backoff: 1s -> 2s -> 4s
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] Attempt {}/{}: Retry sleep interrupted for {}",
                         correlationId, attempt, maxAttempts, path);
                if (attempt >= maxAttempts) break;
            } catch (Exception e) {
                String msg = e.getMessage();
                log.warn("[{}] Attempt {}/{}: Structured generation call failed on {}: {}",
                         correlationId, attempt, maxAttempts, path, msg);

                // Check if this error is non-retryable (auth, 404, etc)
                if (shouldNotRetry(e, msg)) {
                    log.warn("[{}] Non-retryable error detected ({}), failing immediately", correlationId, msg);
                    break;
                }

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                        delayMs *= 2; // exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[{}] Attempt {}/{}: Retry sleep interrupted",
                                 correlationId, attempt, maxAttempts);
                        break;
                    }
                }
            }
        }

        log.error("[{}] Structured generation failed after {} attempts on {} — returning empty",
                  correlationId, maxAttempts, path);
        return Optional.empty();
    }

    /** Non-retryable errors: auth, 4xx client errors. Retryable: transient, 5xx, timeout. */
    private boolean shouldNotRetry(Exception e, String msg) {
        if (msg == null) return false;
        String lowerMsg = msg.toLowerCase();
        // Authentication, authorization, not found → don't waste retries
        return lowerMsg.contains("authentication")
            || lowerMsg.contains("unauthorized")
            || lowerMsg.contains("forbidden")
            || lowerMsg.contains("404")
            || lowerMsg.contains("not found");
    }

    /** Extract correlationId from request map if present, otherwise generate one. */
    private String extractCorrelationId(Object request) {
        if (request instanceof java.util.Map) {
            Object cid = ((java.util.Map<?, ?>) request).get("correlationId");
            if (cid != null) return cid.toString();
        }
        return "struct-" + System.nanoTime();
    }

    /** comment 생성 — 피기백 반응 JSON 포함 버전. */
    public Optional<GenResult> generateCommentR(GenDto.CommentRequest req) {
        return generateWithReactions("/generate/comment", req);
    }

    /** reply 생성 — 피기백 반응 JSON 포함 버전. */
    public Optional<GenResult> generateReplyR(GenDto.ReplyRequest req) {
        return generateWithReactions("/generate/reply", req);
    }

    private Optional<GenResult> generateWithReactions(String path, Object req) {
        try {
            GenDto.Response resp = restClient.post()
                .uri(path)
                .body(req)
                .retrieve()
                .body(GenDto.Response.class);
            if (resp != null && resp.isSuccess()) {
                return Optional.of(new GenResult(resp.getText(), resp.getReactionsJson()));
            }
            if (resp != null && resp.getError() != null) {
                log.warn("Gen error on {}: type={} msg={}", path, resp.getErrorType(), resp.getError());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("LlmAiUser call failed on {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 글 분석 호출 → JSON 문자열 반환 (실패 시 empty). 좋아요·투표 결정용 신호.
     * archetypeHints: 카테고리별 후보 archetype id (콤마 구분), 없으면 null.
     */
    public Optional<String> analyzePost(String postId, String title, String body,
                                        String category, String archetypeHints) {
        try {
            Map<String, Object> req = new java.util.HashMap<>();
            req.put("postId", postId != null ? postId : "");
            req.put("title", title != null ? title : "");
            req.put("bodyPublished", body != null ? body : "");
            req.put("category", category != null ? category : "");
            if (archetypeHints != null && !archetypeHints.isBlank()) {
                req.put("archetypeHints", archetypeHints);
            }
            req.put("correlationId", "post-analysis-" + System.nanoTime());
            GenDto.Response resp = restClient.post()
                .uri("/analyze/post")
                .body(req)
                .retrieve()
                .body(GenDto.Response.class);
            if (resp != null && resp.isSuccess()) {
                return Optional.of(resp.getText());
            }
            if (resp != null && resp.getError() != null) {
                log.warn("Post analysis error: type={} msg={}", resp.getErrorType(), resp.getError());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("LlmAiUser analyzePost call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> generate(String path, Object req) {
        try {
            GenDto.Response resp = restClient.post()
                .uri(path)
                .body(req)
                .retrieve()
                .body(GenDto.Response.class);
            if (resp != null && resp.isSuccess()) {
                return Optional.of(resp.getText());
            }
            if (resp != null && resp.getError() != null) {
                log.warn("Gen error on {}: type={} msg={}", path, resp.getErrorType(), resp.getError());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("LlmAiUser call failed on {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    /** 페르소나 voice JSON 생성 */
    public Optional<String> generatePersonaVoice(String prompt) {
        try {
            GenDto.Response resp = restClient.post()
                .uri("/generate/persona")
                .body(Map.of("prompt", prompt, "correlationId", "persona-gen-" + System.nanoTime()))
                .retrieve()
                .body(GenDto.Response.class);
            if (resp == null || resp.getText() == null || resp.getText().isBlank()) return Optional.empty();
            return Optional.of(resp.getText());
        } catch (Exception e) {
            log.warn("generatePersonaVoice failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
