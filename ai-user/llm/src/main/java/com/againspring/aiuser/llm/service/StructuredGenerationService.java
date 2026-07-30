package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.*;
import com.againspring.aiuser.llm.exception.LlmCapacityException;
import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Builds isolated JSON-only prompts and rejects malformed or unsafe model output locally. */
@Service
@RequiredArgsConstructor
public class StructuredGenerationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern META = Pattern.compile("(?i)(적용 처리 메모|작성 노트|<<<|```|i can't help|i am (claude|codex))");
    private final LlmWorkerPool pool;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}") private String claudeDefault;
    @Value("${llm.post-model:claude-sonnet-4-6}") private String claudePostModel;
    @Value("${llm.worker.codex-terra-model:gpt-5.6-terra}") private String codexTerra;
    @Value("${llm.worker.codex-luna-model:gpt-5.6-luna}") private String codexLuna;

    public ThreadPlanResponse createThreadPlan(ThreadPlanRequest req, String correlationId) {
        validatePlanRequest(req);
        LlmProvider provider = LlmProvider.parse(req.getProvider());
        String model = resolvePlanModel(req, provider);
        long started = System.currentTimeMillis();
        return withOneRetry(() -> pool.executeProviderTask(planPrompt(req), model, timeout(req.getTimeoutMs()), correlationId, provider,
                        StructuredOutputSchema.THREAD_PLAN),
                raw -> parsePlan(raw, req, provider, model, correlationId, started));
    }

    public HumanReplyBatchResponse createHumanReplies(HumanReplyBatchRequest req, String correlationId) {
        validateReplyRequest(req);
        LlmProvider provider = LlmProvider.parse(req.getProvider());
        String model = resolveReplyModel(req, provider);
        long started = System.currentTimeMillis();
        return withOneRetry(() -> pool.executeProviderTask(replyPrompt(req), model, timeout(req.getTimeoutMs()), correlationId, provider,
                        StructuredOutputSchema.HUMAN_REPLIES),
                raw -> parseReplies(raw, req, provider, model, correlationId, started));
    }

    private ThreadPlanResponse parsePlan(String raw, ThreadPlanRequest req, LlmProvider provider, String model,
                                         String correlationId, long started) {
        JsonNode root = parseObject(raw);
        ThreadPlanResponse.Post post = null;
        if (req.getKind() == ThreadPlanRequest.Kind.AI_POST) {
            JsonNode p = root.path("post");
            String title = text(p, "title"); String body = text(p, "body");
            validText(title, "post.title", 4, 160); validText(body, "post.body", 20, 6000);
            post = ThreadPlanResponse.Post.builder().title(title).body(body).build();
        }
        JsonNode comments = root.path("comments");
        if (!comments.isArray()) throw new StructuredGenerationException("comments must be an array");
        int max = safe(req.getMaxTopLevel(), 14, 1, 20) + safe(req.getMaxReplies(), 10, 0, 20);
        if (comments.size() > max) throw new StructuredGenerationException("comment candidate limit exceeded");
        Set<String> personaIds = new HashSet<>();
        for (var p : req.getPersonas()) personaIds.add(p.getPersonaId());
        Set<String> refs = new HashSet<>(); Set<String> topLevel = new HashSet<>();
        List<ThreadPlanResponse.Item> items = new ArrayList<>();
        for (JsonNode n : comments) {
            String ref = text(n, "ref"); String parent = nullableText(n, "parentRef");
            String persona = text(n, "personaId"); String body = text(n, "body");
            validRef(ref); validText(body, "comment.body", 2, 1000);
            if (!refs.add(ref)) throw new StructuredGenerationException("duplicate comment ref");
            if (!personaIds.contains(persona)) throw new StructuredGenerationException("unknown personaId: " + persona);
            if (parent == null) topLevel.add(ref);
            else if (!topLevel.contains(parent)) throw new StructuredGenerationException("reply parent must be an earlier top-level ref");
            items.add(ThreadPlanResponse.Item.builder().ref(ref).parentRef(parent).personaId(persona).body(body)
                    .stance(nullableText(n, "stance")).priority(n.path("priority").asInt(0)).build());
        }
        int minTop = Math.min(6, safe(req.getMaxTopLevel(), 14, 1, 20));
        if (topLevel.size() < minTop || items.size() < Math.min(12, max))
            throw new StructuredGenerationException("thread plan does not meet minimum candidate count");
        return ThreadPlanResponse.builder().provider(provider.name()).model(model).correlationId(correlationId)
                .post(post).items(items).elapsedMs(System.currentTimeMillis() - started).build();
    }

    private HumanReplyBatchResponse parseReplies(String raw, HumanReplyBatchRequest req, LlmProvider provider,
                                                  String model, String correlationId, long started) {
        JsonNode replies = parseObject(raw).path("replies");
        if (!replies.isArray()) throw new StructuredGenerationException("replies must be an array");
        Map<Long, HumanReplyBatchRequest.Item> expected = new HashMap<>();
        for (var item : req.getItems()) expected.put(item.getHumanCommentId(), item);
        Set<Long> seen = new HashSet<>(); List<HumanReplyBatchResponse.Reply> parsed = new ArrayList<>();
        for (JsonNode n : replies) {
            long humanId = n.path("humanCommentId").asLong(-1); String persona = text(n, "personaId"); String body = text(n, "body");
            var input = expected.get(humanId);
            if (input == null || !seen.add(humanId)) throw new StructuredGenerationException("unknown or duplicate humanCommentId");
            if (!persona.equals(input.getResponder().getPersonaId())) throw new StructuredGenerationException("reply persona mismatch");
            validText(body, "reply.body", 2, 1000);
            parsed.add(HumanReplyBatchResponse.Reply.builder().humanCommentId(humanId).personaId(persona).body(body).build());
        }
        return HumanReplyBatchResponse.builder().provider(provider.name()).model(model).correlationId(correlationId)
                .replies(parsed).elapsedMs(System.currentTimeMillis() - started).build();
    }

    private String planPrompt(ThreadPlanRequest req) {
        String existing = req.getKind() == ThreadPlanRequest.Kind.HUMAN_POST
                ? "EXISTING_POST=" + json(Map.of("title", clean(req.getExistingTitle()), "body", clean(req.getExistingBody()))) : "";
        return """
                Return ONLY a JSON object, with no Markdown or explanation. Create Korean community conversation candidates.
                The JSON schema is {"post":{"title":"...","body":"..."},"comments":[{"ref":"c1","parentRef":null,"personaId":"...","body":"...","stance":"...","priority":1}]}.
                For a HUMAN_POST set post to null; for AI_POST include post. Use only supplied personaIds. A reply's parentRef must refer to an earlier top-level comment. Do not use legal verdicts, diagnoses, personal data, internal notes, or claims of being an AI.
                """ + "\nKIND=" + req.getKind() + "\nCATEGORY=" + clean(req.getCategory()) + "\nTOPIC=" + clean(req.getTopicHint()) + "\n" + existing +
                "\nPERSONAS=" + json(req.getPersonas()) + "\nLIMITS=" + json(Map.of("topLevel", safe(req.getMaxTopLevel(),14,1,20), "replies", safe(req.getMaxReplies(),10,0,20)));
    }

    private String replyPrompt(HumanReplyBatchRequest req) {
        return """
                Return ONLY {"replies":[{"humanCommentId":1,"personaId":"...","body":"..."}]}. Write one short natural Korean reply for each supplied human comment. Use exactly its assigned responder personaId. Do not use internal notes, legal verdicts, diagnoses, personal data, or claims of being an AI.
                INPUT=""" + json(req.getItems().stream().map(i -> Map.of("humanCommentId", i.getHumanCommentId(), "postTitle", clean(i.getPostTitle()), "postBody", clean(i.getPostBody()), "humanBody", clean(i.getHumanBody()), "responder", i.getResponder())).toList());
    }

    private JsonNode parseObject(String raw) {
        try {
            JsonNode root = JSON.readTree(raw == null ? "" : raw.trim());
            if (root == null || !root.isObject()) throw new StructuredGenerationException("response is not a JSON object");
            return root;
        } catch (StructuredGenerationException e) { throw e;
        } catch (Exception e) { throw new StructuredGenerationException("invalid JSON response"); }
    }
    private void validatePlanRequest(ThreadPlanRequest r) {
        if (r == null || r.getKind() == null || r.getPersonas() == null || r.getPersonas().isEmpty()) throw new IllegalArgumentException("kind and personas are required");
        if (r.getKind() == ThreadPlanRequest.Kind.HUMAN_POST && (blank(r.getExistingTitle()) || blank(r.getExistingBody()))) throw new IllegalArgumentException("existing post is required");
        for (var p : r.getPersonas()) if (p == null || blank(p.getPersonaId())) throw new IllegalArgumentException("personaId is required");
    }
    private void validateReplyRequest(HumanReplyBatchRequest r) {
        if (r == null || r.getItems() == null || r.getItems().isEmpty() || r.getItems().size() > 50) throw new IllegalArgumentException("1..50 reply items required");
        for (var i : r.getItems()) if (i == null || i.getHumanCommentId() == null || i.getResponder() == null || blank(i.getResponder().getPersonaId()) || blank(i.getHumanBody())) throw new IllegalArgumentException("humanCommentId, responder and humanBody are required");
    }
    private String resolvePlanModel(ThreadPlanRequest r, LlmProvider p) {
        String configured = p == LlmProvider.CODEX ? (r.getKind() == ThreadPlanRequest.Kind.AI_POST ? codexTerra : codexLuna) : (r.getKind() == ThreadPlanRequest.Kind.AI_POST ? claudePostModel : claudeDefault);
        return configuredModel(r.getModel(), configured);
    }
    private String resolveReplyModel(HumanReplyBatchRequest r, LlmProvider p) { return configuredModel(r.getModel(), p == LlmProvider.CODEX ? codexLuna : claudeDefault); }
    private static String configuredModel(String requested, String configured) {
        if (blank(configured)) throw new IllegalStateException("No model configured for selected provider/workload");
        if (!blank(requested) && !requested.trim().equals(configured)) throw new IllegalArgumentException("model override is not allowed for structured generation");
        return configured;
    }
    private <T> T withOneRetry(java.util.concurrent.Callable<String> call, Function<String, T> parse) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try { return parse.apply(call.call()); }
            catch (LlmCapacityException | LlmTimeoutException e) { throw e; }
            catch (RuntimeException e) { last = e; }
            catch (Exception e) { last = new StructuredGenerationException("generation invocation failed"); }
        }
        throw last;
    }
    private static long timeout(Long v) { return v == null ? 120_000L : Math.max(1_000L, Math.min(v, 300_000L)); }
    private static int safe(Integer v, int d, int min, int max) { return Math.max(min, Math.min(v == null ? d : v, max)); }
    private static String text(JsonNode n, String name) { String v = nullableText(n, name); if (v == null) throw new StructuredGenerationException(name + " is required"); return v; }
    private static String nullableText(JsonNode n, String name) { return n.hasNonNull(name) ? n.path(name).asText().trim() : null; }
    private static void validRef(String v) { if (!v.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) throw new StructuredGenerationException("invalid ref"); }
    private static void validText(String v, String field, int min, int max) { if (v.length() < min || v.length() > max || LlmErrorSignature.looksLikeProviderError(v) || META.matcher(v).find() || koreanRatio(v) < .10) throw new StructuredGenerationException("invalid " + field); }
    private static double koreanRatio(String v) { long k=v.chars().filter(c -> c >= 0xAC00 && c <= 0xD7A3).count(); long letters=v.chars().filter(Character::isLetter).count(); return letters == 0 ? 1 : (double) k / letters; }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String clean(String s) { return s == null ? "" : s.replace('<','＜').replace('>','＞').replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").substring(0, Math.min(s.length(), 5000)); }
    private static String json(Object value) { try { return JSON.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Cannot serialize structured prompt input", e); } }
}
