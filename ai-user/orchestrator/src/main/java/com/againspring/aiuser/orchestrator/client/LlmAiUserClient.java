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

    /** One bounded request for the 30-minute human-comment response batch. */
    public Optional<java.util.Map<String, Object>> generateHumanReplies(java.util.Map<String, Object> request) {
        return generateStructured("/v2/generate/human-replies", request);
    }

    private Optional<java.util.Map<String, Object>> generateStructured(String path, Object request) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restClient.post().uri(path).body(request).retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Object>>() {});
            if (response != null && !response.containsKey("errorCode")) return Optional.of(response);
            log.warn("Structured generation failed on {}: {}", path, response == null ? "empty" : response.get("errorCode"));
        } catch (Exception e) {
            log.warn("Structured generation call failed on {}: {}", path, e.getMessage());
        }
        return Optional.empty();
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
