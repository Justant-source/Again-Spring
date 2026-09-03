package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.PostRewriteRequest;
import com.againspring.aiuser.llm.dto.PostRewriteResponse;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostRewriteService {

    private final LlmWorkerPool pool;
    private final PromptAssembler promptAssembler;
    private final OutputSanitizer outputSanitizer;
    private final ObjectMapper objectMapper;

    /** 글 rewrite도 post 전용 승격 모델을 그대로 사용한다. */
    @Value("${llm.post-model:}")
    private String postModel;

    public PostRewriteResponse rewrite(PostRewriteRequest req, String correlationId, long startMs) throws Exception {
        String prompt = promptAssembler.assemblePostRewritePrompt(req);
        String model = (postModel != null && !postModel.isBlank()) ? postModel.trim() : null;
        String raw = pool.executeSyncTask(prompt, model, req.getTimeoutMs(), correlationId, req.resolveProvider());
        ParsedRewrite parsed = parseRewriteResponse(raw, req);
        return PostRewriteResponse.success(
            parsed.title(),
            parsed.body(),
            System.currentTimeMillis() - startMs,
            correlationId
        );
    }

    private ParsedRewrite parseRewriteResponse(String raw, PostRewriteRequest req) throws Exception {
        JsonNode node = parseJson(raw);
        String title = sanitizeTitle(node.path("title").asText(req.getOriginalTitle()));
        String body = outputSanitizer.sanitizePost(node.path("body").asText(""), req.getVoiceType());
        if (title.isBlank()) {
            title = sanitizeTitle(req.getOriginalTitle());
        }
        if (body.length() < 60) {
            throw new IllegalStateException("rewritten body is too short");
        }
        return new ParsedRewrite(title, body);
    }

    private JsonNode parseJson(String raw) throws Exception {
        String json = raw != null ? raw.trim() : "";
        if (json.contains("```json")) {
            int s = json.indexOf("```json") + 7;
            int e = json.lastIndexOf("```");
            if (e > s) {
                return objectMapper.readTree(json.substring(s, e).trim());
            }
        }
        if (json.contains("```")) {
            int s = json.indexOf("```") + 3;
            int e = json.lastIndexOf("```");
            if (e > s) {
                String candidate = json.substring(s, e).trim();
                if (candidate.startsWith("{")) {
                    return objectMapper.readTree(candidate);
                }
            }
        }
        int bs = json.indexOf('{');
        int be = json.lastIndexOf('}');
        if (bs >= 0 && be > bs) {
            return objectMapper.readTree(json.substring(bs, be + 1));
        }
        throw new IllegalStateException("rewrite response does not contain JSON");
    }

    private String sanitizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String title = raw
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace("제목:", "")
            .replace("title:", "")
            .replace("\"", "")
            .replace("`", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (title.length() > 40) {
            title = title.substring(0, 40).trim();
        }
        return title;
    }

    private record ParsedRewrite(String title, String body) {}
}
