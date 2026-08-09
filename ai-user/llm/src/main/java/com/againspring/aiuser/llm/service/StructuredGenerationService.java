package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.*;
import com.againspring.aiuser.llm.exception.LlmCapacityException;
import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Builds isolated JSON-only prompts and rejects malformed or unsafe model output locally. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredGenerationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Value("${llm.worker.default-timeout-ms:600000}")
    private long defaultTimeoutMs;

    private static final Pattern META = Pattern.compile("(?i)(적용 처리 메모|작성 노트|<<<|```|i can't help|i am (claude|codex))");
    /** Marketing X/IG capture: bodies with more than this many non-empty newline blocks need a split. */
    public static final int SHORT_POST_MAX_BLOCKS = 8;
    public static final int MAX_PARTS_PER_SIDE = 4;
    private final LlmWorkerPool pool;
    private final SelfCritiqueService selfCritique;

    @Value("${llm.worker.claude-model:claude-haiku-4-5-20251001}") private String claudeDefault;
    @Value("${llm.post-model:claude-sonnet-4-6}") private String claudePostModel;
    @Value("${llm.worker.codex-terra-model:gpt-5.6-terra}") private String codexTerra;
    @Value("${llm.worker.codex-luna-model:gpt-5.6-luna}") private String codexLuna;

    public ThreadPlanResponse createThreadPlan(ThreadPlanRequest req, String correlationId) {
        validatePlanRequest(req);
        LlmProvider provider = LlmProvider.parse(req.getProvider());
        String model = resolvePlanModel(req, provider);
        long started = System.currentTimeMillis();
        String prompt = planPrompt(req);
        return withOneRetry(() -> pool.executeProviderTask(prompt, model, timeout(req.getTimeoutMs()), correlationId, provider,
                        StructuredOutputSchema.THREAD_PLAN),
                raw -> applySelfCritique(parsePlan(raw, req, provider, model, correlationId, started),
                        req, prompt, model, correlationId));
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

    /**
     * Logical Call1 — 작성자 post + phase1 comments (author-only grounding).
     * Workload id: {@link StructuredOutputSchema#WORKLOAD_PAIRED_PHASE1}.
     */
    public PairedPhase1Response createPairedPhase1(PairedPhase1Request req, String correlationId) {
        validatePairedPhase1Request(req);
        LlmProvider provider = LlmProvider.parse(req.getProvider());
        String model = resolvePairedPostModel(req.getModel(), provider);
        long started = System.currentTimeMillis();
        String prompt = pairedPhase1Prompt(req);
        return withOneRetry(() -> pool.executeProviderTask(prompt, model, timeout(req.getTimeoutMs()), correlationId, provider,
                        StructuredOutputSchema.PAIRED_PHASE1),
                raw -> applyPairedPhase1Critique(
                        parsePairedPhase1(raw, req, provider, model, correlationId, started),
                        req, prompt, model, correlationId));
    }

    /**
     * Logical Call2 — 상대방 body + phase2 comments.
     * Workload id: {@link StructuredOutputSchema#WORKLOAD_PAIRED_PHASE2}.
     * When cast is large, orchestrator may follow with {@code includePartnerPost=false}
     * comment-only calls — still the same logical Call2.
     */
    public PairedPhase2Response createPairedPhase2(PairedPhase2Request req, String correlationId) {
        validatePairedPhase2Request(req);
        LlmProvider provider = LlmProvider.parse(req.getProvider());
        String model = resolvePairedPostModel(req.getModel(), provider);
        long started = System.currentTimeMillis();
        String prompt = pairedPhase2Prompt(req);
        return withOneRetry(() -> pool.executeProviderTask(prompt, model, timeout(req.getTimeoutMs()), correlationId, provider,
                        StructuredOutputSchema.PAIRED_PHASE2),
                raw -> applyPairedPhase2Critique(
                        parsePairedPhase2(raw, req, provider, model, correlationId, started),
                        req, prompt, model, correlationId));
    }

    private ThreadPlanResponse parsePlan(String raw, ThreadPlanRequest req, LlmProvider provider, String model,
                                         String correlationId, long started) {
        JsonNode root = parseObject(raw);
        ThreadPlanResponse.Post post = null;
        if (req.getKind() == ThreadPlanRequest.Kind.AI_POST) {
            JsonNode p = root.path("post");
            String title = text(p, "title"); String body = text(p, "body");
            // 제목 ≤40자(공백 포함)·제목≠본문 — 2026-08 prod 동일 제목/본문 회귀 방어
            validText(title, "post.title", 4, 40); validText(body, "post.body", 20, 6000);
            rejectIdenticalTitleBody(title, body);
            List<Integer> splits = sanitizeCaptureSplits(body, readCaptureSplits(p));
            Integer legacy = (splits != null && !splits.isEmpty()) ? splits.get(0) : null;
            String promoTitle = sanitizePromoTitle(title, nullableText(p, "promo_title"));
            List<String> metaphorIds = MetaphorCatalog.sanitizeList(readMetaphorIds(p), req.getCategory());
            String metaphorId = metaphorIds.isEmpty() ? null : metaphorIds.get(0);
            post = ThreadPlanResponse.Post.builder()
                    .title(title).body(body).promoTitle(promoTitle)
                    .captureSplitAfterLines(splits)
                    .captureSplitAfterLine(legacy)
                    .metaphorId(metaphorId)
                    .metaphorIds(metaphorIds)
                    .build();
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
        int maxTop = safe(req.getMaxTopLevel(), 14, 1, 20);
        // null → legacy floors (6 top / 12 items); explicit values (incl. 1) honored for quality-droppable plans
        int minTop = req.getMinTopLevel() != null
                ? Math.max(1, Math.min(req.getMinTopLevel(), maxTop))
                : Math.min(6, maxTop);
        int minItems = req.getMinItems() != null
                ? Math.max(1, Math.min(req.getMinItems(), max))
                : Math.min(12, max);
        if (topLevel.size() < minTop || items.size() < minItems)
            throw new StructuredGenerationException("thread plan does not meet minimum candidate count");
        return ThreadPlanResponse.builder().provider(provider.name()).model(model).correlationId(correlationId)
                .post(post).items(items).elapsedMs(System.currentTimeMillis() - started).build();
    }

    /**
     * Same SelfCritique gate as legacy {@code GenerationController}: post + top-level comments.
     * Replies are skipped (short text). Failed refine keeps the original body.
     */
    private ThreadPlanResponse applySelfCritique(ThreadPlanResponse parsed, ThreadPlanRequest req, String prompt,
                                                 String model, String correlationId) {
        if (selfCritique == null || parsed == null) return parsed;
        // Critique refine uses session CLI (same as legacy GenerationController), independent of PLAN provider.
        String backend = "CLI";
        Map<String, ThreadPlanRequest.Persona> byId = new HashMap<>();
        for (ThreadPlanRequest.Persona p : req.getPersonas()) {
            if (p != null && !blank(p.getPersonaId())) byId.put(p.getPersonaId(), p);
        }

        ThreadPlanResponse.Post post = parsed.getPost();
        if (post != null) {
            ThreadPlanRequest.Persona author = resolveAuthorPersona(req);
            String refined = selfCritique.critiqueAndRefine(
                    post.getBody(), "post", prompt, correlationId, backend,
                    resolveFormality(author), model, resolveVoiceType(author));
            post = replacePostBodyIfValid(post, refined);
        }

        List<ThreadPlanResponse.Item> items = new ArrayList<>(parsed.getItems().size());
        for (ThreadPlanResponse.Item item : parsed.getItems()) {
            if (item.getParentRef() != null) {
                items.add(item);
                continue;
            }
            ThreadPlanRequest.Persona persona = byId.get(item.getPersonaId());
            String refined = selfCritique.critiqueAndRefine(
                    item.getBody(), "comment", prompt, correlationId + "-" + item.getRef(), backend,
                    resolveFormality(persona), model, resolveVoiceType(persona));
            items.add(replaceCommentBodyIfValid(item, refined));
        }

        return ThreadPlanResponse.builder()
                .provider(parsed.getProvider()).model(parsed.getModel()).correlationId(parsed.getCorrelationId())
                .post(post).items(items).elapsedMs(parsed.getElapsedMs()).build();
    }

    private static ThreadPlanResponse.Post replacePostBodyIfValid(ThreadPlanResponse.Post post, String refined) {
        if (refined == null || refined.equals(post.getBody())) return post;
        try {
            validText(refined, "post.body", 20, 6000);
            List<Integer> splits = sanitizeCaptureSplits(refined, post.getCaptureSplitAfterLines());
            Integer legacy = (splits != null && !splits.isEmpty()) ? splits.get(0) : null;
            return ThreadPlanResponse.Post.builder()
                    .title(post.getTitle()).body(refined)
                    .promoTitle(post.getPromoTitle())
                    .captureSplitAfterLines(splits)
                    .captureSplitAfterLine(legacy)
                    .metaphorId(post.getMetaphorId())
                    .metaphorIds(post.getMetaphorIds())
                    .build();
        } catch (StructuredGenerationException ignored) {
            return post;
        }
    }

    private static ThreadPlanResponse.Item replaceCommentBodyIfValid(ThreadPlanResponse.Item item, String refined) {
        if (refined == null || refined.equals(item.getBody())) return item;
        try {
            validText(refined, "comment.body", 2, 1000);
            return ThreadPlanResponse.Item.builder()
                    .ref(item.getRef()).parentRef(item.getParentRef()).personaId(item.getPersonaId())
                    .body(refined).stance(item.getStance()).priority(item.getPriority()).build();
        } catch (StructuredGenerationException ignored) {
            return item;
        }
    }

    /**
     * Prefer explicit {@code author.personaId} match in the cast; else personas[0].
     * Orchestrator should put the AI_POST author first, but this keeps SelfCritique aligned
     * when the author Map is sent separately.
     */
    static ThreadPlanRequest.Persona resolveAuthorPersona(ThreadPlanRequest req) {
        if (req == null || req.getPersonas() == null || req.getPersonas().isEmpty()) return null;
        String authorId = null;
        if (req.getAuthor() != null && req.getAuthor().get("personaId") != null) {
            authorId = String.valueOf(req.getAuthor().get("personaId")).trim();
        }
        if (!blank(authorId)) {
            for (ThreadPlanRequest.Persona p : req.getPersonas()) {
                if (p != null && authorId.equals(p.getPersonaId())) return p;
            }
        }
        return req.getPersonas().get(0);
    }

    /** Prefer top-level formality; fall back to voiceProfile.formality. */
    static String resolveFormality(ThreadPlanRequest.Persona persona) {
        if (persona == null) return null;
        if (!blank(persona.getFormality())) return persona.getFormality().trim();
        Object fromMap = voiceField(persona.getVoiceProfile(), "formality");
        return fromMap == null ? null : String.valueOf(fromMap).trim();
    }

    static String resolveVoiceType(ThreadPlanRequest.Persona persona) {
        if (persona == null) return null;
        Object fromMap = voiceField(persona.getVoiceProfile(), "voice_type");
        if (fromMap == null) fromMap = voiceField(persona.getVoiceProfile(), "voiceType");
        return fromMap == null ? null : String.valueOf(fromMap).trim();
    }

    private static Object voiceField(Map<String, Object> voice, String key) {
        if (voice == null || key == null) return null;
        Object v = voice.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : v;
    }

    private HumanReplyBatchResponse parseReplies(String raw, HumanReplyBatchRequest req, LlmProvider provider,
                                                  String model, String correlationId, long started) {
        JsonNode replies = parseObject(raw).path("replies");
        if (!replies.isArray()) throw new StructuredGenerationException("replies must be an array");
        Map<Long, HumanReplyBatchRequest.Item> expected = new HashMap<>();
        Map<Long, Set<String>> allowedPersonas = new HashMap<>();
        for (var item : req.getItems()) {
            expected.put(item.getHumanCommentId(), item);
            Set<String> ids = new HashSet<>();
            if (item.getCandidateResponders() != null) {
                for (var p : item.getCandidateResponders()) {
                    if (p != null && !blank(p.getPersonaId())) ids.add(p.getPersonaId());
                }
            }
            allowedPersonas.put(item.getHumanCommentId(), ids);
        }
        Map<Long, Integer> counts = new HashMap<>();
        Set<String> seenPairs = new HashSet<>();
        List<HumanReplyBatchResponse.Reply> parsed = new ArrayList<>();
        for (JsonNode n : replies) {
            long humanId = n.path("humanCommentId").asLong(-1);
            String persona = text(n, "personaId");
            String body = text(n, "body");
            if (!expected.containsKey(humanId)) throw new StructuredGenerationException("unknown humanCommentId");
            int next = counts.getOrDefault(humanId, 0) + 1;
            if (next > 3) throw new StructuredGenerationException("more than 3 replies for humanCommentId");
            counts.put(humanId, next);
            String pair = humanId + ":" + persona;
            if (!seenPairs.add(pair)) throw new StructuredGenerationException("duplicate persona for humanCommentId");
            Set<String> allowed = allowedPersonas.getOrDefault(humanId, Set.of());
            if (!allowed.contains(persona)) throw new StructuredGenerationException("reply persona not in candidateResponders");
            validText(body, "reply.body", 2, 1000);
            parsed.add(HumanReplyBatchResponse.Reply.builder().humanCommentId(humanId).personaId(persona).body(body).build());
        }
        return HumanReplyBatchResponse.builder().provider(provider.name()).model(model).correlationId(correlationId)
                .replies(parsed).elapsedMs(System.currentTimeMillis() - started).build();
    }

    private PairedPhase1Response parsePairedPhase1(String raw, PairedPhase1Request req, LlmProvider provider,
                                                   String model, String correlationId, long started) {
        JsonNode root = parseObject(raw);
        JsonNode p = root.path("post");
        String title = text(p, "title");
        String body = text(p, "body");
        validText(title, "post.title", 4, 40);
        validText(body, "post.body", 20, 6000);
        rejectIdenticalTitleBody(title, body);
        List<Integer> splits = sanitizeCaptureSplits(body, readCaptureSplits(p));
        Integer legacy = (splits != null && !splits.isEmpty()) ? splits.get(0) : null;
        String promoTitle = sanitizePromoTitle(title, nullableText(p, "promo_title"));
        List<String> metaphorIds = MetaphorCatalog.sanitizeList(readMetaphorIds(p), req.getCategory());
        String metaphorId = metaphorIds.isEmpty() ? null : metaphorIds.get(0);
        ThreadPlanResponse.Post post = ThreadPlanResponse.Post.builder()
                .title(title).body(body).promoTitle(promoTitle)
                .captureSplitAfterLines(splits)
                .captureSplitAfterLine(legacy)
                .metaphorId(metaphorId)
                .metaphorIds(metaphorIds)
                .build();

        int maxTop = safe(req.getMaxTopLevel(), 4, 1, 6);
        int maxReplies = safe(req.getMaxReplies(), 2, 0, 6);
        int max = maxTop + maxReplies;
        int minTop = req.getMinTopLevel() != null
                ? Math.max(1, Math.min(req.getMinTopLevel(), maxTop))
                : Math.min(2, maxTop);
        int minItems = req.getMinItems() != null
                ? Math.max(1, Math.min(req.getMinItems(), max))
                : minTop;
        List<ThreadPlanResponse.Item> items = parseCommentItems(root.path("comments"), req.getPersonas(), max, minTop, minItems);
        return PairedPhase1Response.builder()
                .provider(provider.name()).model(model).correlationId(correlationId)
                .workload(StructuredOutputSchema.WORKLOAD_PAIRED_PHASE1)
                .post(post).items(items).elapsedMs(System.currentTimeMillis() - started).build();
    }

    private PairedPhase2Response parsePairedPhase2(String raw, PairedPhase2Request req, LlmProvider provider,
                                                   String model, String correlationId, long started) {
        JsonNode root = parseObject(raw);
        boolean wantPartner = req.getIncludePartnerPost() == null || Boolean.TRUE.equals(req.getIncludePartnerPost());
        PairedPhase2Response.PartnerPost partnerPost = null;
        JsonNode pp = root.get("partner_post");
        if (pp == null) pp = root.get("partnerPost");
        if (wantPartner) {
            if (pp == null || pp.isNull() || !pp.isObject()) {
                throw new StructuredGenerationException("partner_post is required");
            }
            String body = text(pp, "body");
            validText(body, "partner_post.body", 20, 6000);
            List<Integer> splits = sanitizeCaptureSplits(body, readCaptureSplits(pp));
            partnerPost = PairedPhase2Response.PartnerPost.builder()
                    .body(body)
                    .captureSplitAfterLines(splits)
                    .build();
        } else if (pp != null && !pp.isNull()) {
            throw new StructuredGenerationException("partner_post must be null for comment-only Call2 continuation");
        }

        int maxTop = safe(req.getMaxTopLevel(), 14, 1, 20);
        int maxReplies = safe(req.getMaxReplies(), 10, 0, 20);
        int max = maxTop + maxReplies;
        int minTop = req.getMinTopLevel() != null
                ? Math.max(1, Math.min(req.getMinTopLevel(), maxTop))
                : Math.min(4, maxTop);
        int minItems = req.getMinItems() != null
                ? Math.max(1, Math.min(req.getMinItems(), max))
                : Math.min(6, max);
        List<ThreadPlanResponse.Item> items = parseCommentItems(root.path("comments"), req.getPersonas(), max, minTop, minItems);
        return PairedPhase2Response.builder()
                .provider(provider.name()).model(model).correlationId(correlationId)
                .workload(StructuredOutputSchema.WORKLOAD_PAIRED_PHASE2)
                .partnerPost(partnerPost).items(items).elapsedMs(System.currentTimeMillis() - started).build();
    }

    private List<ThreadPlanResponse.Item> parseCommentItems(JsonNode comments, List<ThreadPlanRequest.Persona> personas,
                                                            int max, int minTop, int minItems) {
        if (!comments.isArray()) throw new StructuredGenerationException("comments must be an array");
        if (comments.size() > max) throw new StructuredGenerationException("comment candidate limit exceeded");
        Set<String> personaIds = new HashSet<>();
        for (var p : personas) personaIds.add(p.getPersonaId());
        Set<String> refs = new HashSet<>();
        Set<String> topLevel = new HashSet<>();
        List<ThreadPlanResponse.Item> items = new ArrayList<>();
        for (JsonNode n : comments) {
            String ref = text(n, "ref");
            String parent = nullableText(n, "parentRef");
            String persona = text(n, "personaId");
            String body = text(n, "body");
            validRef(ref);
            validText(body, "comment.body", 2, 1000);
            if (!refs.add(ref)) throw new StructuredGenerationException("duplicate comment ref");
            if (!personaIds.contains(persona)) throw new StructuredGenerationException("unknown personaId: " + persona);
            if (parent == null) topLevel.add(ref);
            else if (!topLevel.contains(parent)) throw new StructuredGenerationException("reply parent must be an earlier top-level ref");
            items.add(ThreadPlanResponse.Item.builder().ref(ref).parentRef(parent).personaId(persona).body(body)
                    .stance(nullableText(n, "stance")).priority(n.path("priority").asInt(0)).build());
        }
        if (topLevel.size() < minTop || items.size() < minItems) {
            throw new StructuredGenerationException("thread plan does not meet minimum candidate count");
        }
        return items;
    }

    private PairedPhase1Response applyPairedPhase1Critique(PairedPhase1Response parsed, PairedPhase1Request req,
                                                           String prompt, String model, String correlationId) {
        if (selfCritique == null || parsed == null) return parsed;
        ThreadPlanRequest bridge = new ThreadPlanRequest();
        bridge.setAuthor(req.getAuthor());
        bridge.setPersonas(req.getPersonas());
        ThreadPlanResponse critiqued = applySelfCritique(
                ThreadPlanResponse.builder()
                        .provider(parsed.getProvider()).model(parsed.getModel()).correlationId(parsed.getCorrelationId())
                        .post(parsed.getPost()).items(parsed.getItems()).elapsedMs(parsed.getElapsedMs()).build(),
                bridge, prompt, model, correlationId);
        return PairedPhase1Response.builder()
                .provider(critiqued.getProvider()).model(critiqued.getModel()).correlationId(critiqued.getCorrelationId())
                .workload(parsed.getWorkload())
                .post(critiqued.getPost()).items(critiqued.getItems()).elapsedMs(critiqued.getElapsedMs()).build();
    }

    private PairedPhase2Response applyPairedPhase2Critique(PairedPhase2Response parsed, PairedPhase2Request req,
                                                           String prompt, String model, String correlationId) {
        if (selfCritique == null || parsed == null) return parsed;
        String backend = "CLI";
        Map<String, ThreadPlanRequest.Persona> byId = new HashMap<>();
        for (ThreadPlanRequest.Persona p : req.getPersonas()) {
            if (p != null && !blank(p.getPersonaId())) byId.put(p.getPersonaId(), p);
        }

        PairedPhase2Response.PartnerPost partnerPost = parsed.getPartnerPost();
        if (partnerPost != null) {
            ThreadPlanRequest.Persona partnerPersona = resolvePartnerPersona(req);
            String refined = selfCritique.critiqueAndRefine(
                    partnerPost.getBody(), "post", prompt, correlationId, backend,
                    resolveFormality(partnerPersona), model, resolveVoiceType(partnerPersona));
            if (refined != null && !refined.equals(partnerPost.getBody())) {
                try {
                    validText(refined, "partner_post.body", 20, 6000);
                    List<Integer> splits = sanitizeCaptureSplits(refined, partnerPost.getCaptureSplitAfterLines());
                    partnerPost = PairedPhase2Response.PartnerPost.builder()
                            .body(refined)
                            .captureSplitAfterLines(splits)
                            .build();
                } catch (StructuredGenerationException ignored) {
                    // keep original
                }
            }
        }

        List<ThreadPlanResponse.Item> items = new ArrayList<>(parsed.getItems().size());
        for (ThreadPlanResponse.Item item : parsed.getItems()) {
            if (item.getParentRef() != null) {
                items.add(item);
                continue;
            }
            ThreadPlanRequest.Persona persona = byId.get(item.getPersonaId());
            String refined = selfCritique.critiqueAndRefine(
                    item.getBody(), "comment", prompt, correlationId + "-" + item.getRef(), backend,
                    resolveFormality(persona), model, resolveVoiceType(persona));
            items.add(replaceCommentBodyIfValid(item, refined));
        }
        return PairedPhase2Response.builder()
                .provider(parsed.getProvider()).model(parsed.getModel()).correlationId(parsed.getCorrelationId())
                .workload(parsed.getWorkload())
                .partnerPost(partnerPost).items(items).elapsedMs(parsed.getElapsedMs()).build();
    }

    static ThreadPlanRequest.Persona resolvePartnerPersona(PairedPhase2Request req) {
        if (req == null || req.getPersonas() == null || req.getPersonas().isEmpty()) return null;
        String partnerId = null;
        if (req.getPartner() != null && req.getPartner().get("personaId") != null) {
            partnerId = String.valueOf(req.getPartner().get("personaId")).trim();
        }
        if (!blank(partnerId)) {
            for (ThreadPlanRequest.Persona p : req.getPersonas()) {
                if (p != null && partnerId.equals(p.getPersonaId())) return p;
            }
        }
        return req.getPersonas().get(0);
    }

    private String pairedPhase1Prompt(PairedPhase1Request req) {
        StringBuilder grounding = new StringBuilder();
        if (req.getAuthor() != null && !req.getAuthor().isEmpty()) {
            grounding.append("\nAUTHOR=").append(json(req.getAuthor()));
        }
        if (req.getSourceContext() != null && !req.getSourceContext().isEmpty()) {
            grounding.append("\nSOURCE_CONTEXT=").append(json(cleanValues(req.getSourceContext())));
        }
        if (Boolean.TRUE.equals(req.getReconstructMode())) {
            grounding.append("\nRECONSTRUCT_MODE=true");
            if (req.getSourceExampleId() != null) grounding.append("\nSOURCE_EXAMPLE_ID=").append(req.getSourceExampleId());
            if (!blank(req.getSourceBody())) {
                grounding.append("\nSOURCE_BODY=").append(clean(req.getSourceBody()));
                grounding.append("\nRECONSTRUCT_RULE=Re-narrate the SOURCE_BODY as the author's lived conflict story in their voice. Keep concrete facts/relationships; do not copy sentences verbatim; do not invent a different incident.");
            }
        }
        if (!blank(req.getDynamicExamples())) {
            grounding.append("\nSTYLE_EXAMPLES=").append(clean(req.getDynamicExamples()));
        }
        if (req.getRecentOutputs() != null && !req.getRecentOutputs().isEmpty()) {
            grounding.append("\nRECENT_OUTPUTS=").append(json(req.getRecentOutputs()));
            grounding.append("\nANTI_COPY_RULE=Do not reuse the same incident type, punchline, or closing advice pattern as RECENT_OUTPUTS.");
        }
        int maxTop = safe(req.getMaxTopLevel(), 4, 1, 6);
        int maxReplies = safe(req.getMaxReplies(), 2, 0, 6);
        return """
                Return ONLY a JSON object, with no Markdown or explanation. Workload=PAIRED_PHASE1 (logical Call1).
                Schema: {"post":{"title":"...","body":"...","promo_title":"...\\n...","capture_split_after_lines":null,"metaphor_ids":["empty-chair","tangled-thread","cracked-window"]},"comments":[{"ref":"c1","parentRef":null,"personaId":"...","body":"...","stance":"...","priority":1}]}.
                This is a 양면 사연: write the 작성자(A) post now. The 상대방(B) has NOT written yet — phase1 comments must NOT assume a partner reply exists, quote a partner body, or say both sides already posted.
                Use only supplied personaIds. A reply's parentRef must refer to an earlier top-level comment in this response. Do not use legal verdicts, diagnoses, personal data, internal notes, or claims of being an AI. Prefer Korean product terms 작성자/상대방 — never 가해자/피해자/승패.
                When SOURCE_CONTEXT or SOURCE_BODY is present, the post must be grounded in that source — TOPIC is only a short seed.
                AI_POST title/body rules (hard): title is a short Korean hook of 12~40 characters including spaces (max 40). body must be a separate fuller story — never copy the title into body as the whole body, and never set title equal to body.
                AI_POST promo_title rules (hard): copy title characters exactly; insert semantic newlines only. Each line ideally 4~10 characters (max 10). Never put a single syllable/character alone on a line. Required (IG hook card).
                AI_POST body line rules (hard): one Korean sentence (or short sense unit) per newline — no blank lines; keep each block short (about 1~2 visual lines). If more than 8 non-empty lines, set capture_split_after_lines to 1-based last-block indices of each part except the final (semantic pauses; each part 1~8 blocks; max 4 parts / at most 3 cuts). Prefer even-ish meaningful chunks (e.g. 20 lines → [7,14]). If ≤8 lines, set capture_split_after_lines to null.
                AI_POST metaphor_ids rules (hard): pick 3 to 5 ids from METAPHOR_CATALOG, ordered from best-fit to weakest-fit, that match the emotional core of the story (object-as-feeling metaphor). The FIRST id is the representative/primary metaphor and will be used as the video's opening image — it must be the single best match. The remaining ids will illustrate different beats of the story body, so prefer some variety in mood/tone across the list rather than 5 near-duplicates. Prefer group/tone fit over category label. Required for AI_POST.
                Phase1 comment count: about 2~4 top-level (respect LIMITS). Keep them light reactions to the 작성자 story only.
                """ + "\nGUIDE=\n" + clean(classpathText("voice/paired_phase1.md")) +
                "\nAUTHOR_VOICE=\n" + clean(classpathText("voice/post_paired_author.md")) +
                "\nCATEGORY=" + clean(req.getCategory()) + "\nTOPIC=" + clean(req.getTopicHint()) +
                grounding +
                "\nMETAPHOR_CATALOG=\n" + MetaphorCatalog.compactCatalog() +
                overusedMetaphorsHint(req.getOverusedMetaphorIds()) +
                "\nPERSONAS=" + json(req.getPersonas()) +
                "\nLIMITS=" + json(Map.of("topLevel", maxTop, "replies", maxReplies));
    }

    private String pairedPhase2Prompt(PairedPhase2Request req) {
        boolean wantPartner = req.getIncludePartnerPost() == null || Boolean.TRUE.equals(req.getIncludePartnerPost());
        List<PairedPhase2Request.PublishedComment> published = req.getPublishedTopLevelComments() == null
                ? List.of()
                : req.getPublishedTopLevelComments();
        if (published.size() > 8) published = published.subList(0, 8);
        List<Map<String, Object>> publishedRows = new ArrayList<>();
        for (PairedPhase2Request.PublishedComment c : published) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("body", clean(c.getBody()));
            if (!blank(c.getNickname())) row.put("nickname", clean(c.getNickname()));
            if (!blank(c.getCreatedAt())) row.put("createdAt", clean(c.getCreatedAt()));
            publishedRows.add(row);
        }
        int maxTop = safe(req.getMaxTopLevel(), 14, 1, 20);
        int maxReplies = safe(req.getMaxReplies(), 10, 0, 20);
        String partnerBlock = wantPartner
                ? "Include partner_post with body + optional capture_split_after_lines (same rules as author: ≤8 blocks/part, max 4 parts). Stance=PARTNER."
                : "Set partner_post to null — this is a comment-only micro-batch continuation of logical Call2; do not regenerate the 상대방 body.";
        return """
                Return ONLY a JSON object, with no Markdown or explanation. Workload=PAIRED_PHASE2 (logical Call2).
                Schema: {"partner_post":{"body":"...","capture_split_after_lines":null}|null,"comments":[{"ref":"c1","parentRef":null,"personaId":"...","body":"...","stance":"...","priority":1}]}.
                """ + partnerBlock + "\n" + """
                Phase2 comments are the bulk reactions after both 작성자 and 상대방 bodies are visible.
                Ground comments on AUTHOR_POST + partner body (when being written or already known via partner voice) + PUBLISHED_TOP_LEVEL_COMMENTS.
                If PUBLISHED_TOP_LEVEL_COMMENTS is empty, use author (+ partner) bodies only — do not invent prior comments.
                Do not use published comment ids as parentRef; replies may only parent earlier top-level refs from this response.
                Use only supplied personaIds. No legal verdicts, diagnoses, personal data, internal notes, or AI identity claims. Use 작성자/상대방 — never 가해자/피해자/승패.
                Partner body line rules: one Korean sentence per newline; short blocks. If >8 non-empty lines, set capture_split_after_lines (semantic cuts, each part ≤8, max 4 parts); else null.
                """ + "\nGUIDE=\n" + clean(classpathText("voice/paired_phase2.md")) +
                "\nPARTNER_VOICE=\n" + clean(classpathText("voice/partner.md")) +
                "\nCATEGORY=" + clean(req.getCategory()) +
                "\nAUTHOR_POST=" + json(Map.of(
                        "title", clean(req.getAuthorPost().getTitle()),
                        "body", clean(req.getAuthorPost().getBody()))) +
                "\nPARTNER=" + json(req.getPartner() == null ? Map.of() : req.getPartner()) +
                "\nPUBLISHED_TOP_LEVEL_COMMENTS=" + json(publishedRows) +
                "\nINCLUDE_PARTNER_POST=" + wantPartner +
                "\nPERSONAS=" + json(req.getPersonas()) +
                "\nLIMITS=" + json(Map.of("topLevel", maxTop, "replies", maxReplies));
    }

    private static String classpathText(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String planPrompt(ThreadPlanRequest req) {
        String existing = req.getKind() == ThreadPlanRequest.Kind.HUMAN_POST
                ? "EXISTING_POST=" + json(Map.of("title", clean(req.getExistingTitle()), "body", clean(req.getExistingBody()))) : "";
        StringBuilder grounding = new StringBuilder();
        if (req.getAuthor() != null && !req.getAuthor().isEmpty()) {
            grounding.append("\nAUTHOR=").append(json(req.getAuthor()));
        }
        if (req.getSourceContext() != null && !req.getSourceContext().isEmpty()) {
            grounding.append("\nSOURCE_CONTEXT=").append(json(cleanValues(req.getSourceContext())));
        }
        boolean reconstruct = Boolean.TRUE.equals(req.getReconstructMode());
        if (reconstruct) {
            grounding.append("\nRECONSTRUCT_MODE=true");
            if (req.getSourceExampleId() != null) grounding.append("\nSOURCE_EXAMPLE_ID=").append(req.getSourceExampleId());
            if (!blank(req.getSourceBody())) {
                grounding.append("\nSOURCE_BODY=").append(clean(req.getSourceBody()));
                grounding.append("\nRECONSTRUCT_RULE=Re-narrate the SOURCE_BODY as the author's lived conflict story in their voice. Keep concrete facts/relationships; do not copy sentences verbatim; do not invent a different incident.");
            }
        }
        if (!blank(req.getDynamicExamples())) {
            grounding.append("\nSTYLE_EXAMPLES=").append(clean(req.getDynamicExamples()));
        }
        if (req.getRecentOutputs() != null && !req.getRecentOutputs().isEmpty()) {
            grounding.append("\nRECENT_OUTPUTS=").append(json(req.getRecentOutputs()));
            grounding.append("\nANTI_COPY_RULE=Do not reuse the same incident type, punchline, or closing advice pattern as RECENT_OUTPUTS.");
        }
        return """
                Return ONLY a JSON object, with no Markdown or explanation. Create Korean community conversation candidates.
                The JSON schema is {"post":{"title":"...","body":"...","promo_title":"...\\n...","capture_split_after_lines":null,"metaphor_ids":["empty-chair","tangled-thread","cracked-window"]},"comments":[{"ref":"c1","parentRef":null,"personaId":"...","body":"...","stance":"...","priority":1}]}.
                For a HUMAN_POST set post to null; for AI_POST include post. Use only supplied personaIds. A reply's parentRef must refer to an earlier top-level comment. Do not use legal verdicts, diagnoses, personal data, internal notes, or claims of being an AI.
                When SOURCE_CONTEXT or SOURCE_BODY is present, the post must be grounded in that source — TOPIC is only a short seed, not the whole story.
                AI_POST title/body rules (hard): title is a short Korean hook of 12~40 characters including spaces (max 40). body must be a separate fuller story — never copy the title into body as the whole body, and never set title equal to body. body expands the incident (when/what/how I felt); title only teases the conflict.
                AI_POST promo_title rules (hard): copy title characters exactly; insert semantic newlines only. Each line ideally 4~10 characters (max 10). Never put a single syllable/character alone on a line — pack 1~3 eojeol into a readable phrase. Do not break on every space. No rewrite/omit/add. Required for AI_POST (IG hook card).
                AI_POST body line rules (hard): write body as one Korean sentence (or short sense unit) per newline — no blank lines; each non-empty line is a capture block; keep blocks short (~1–2 visual lines). If more than 8 non-empty lines, set capture_split_after_lines to 1-based last-block indices of each part except the final (semantic pauses; each part 1~8 blocks; max 4 parts / ≤3 cuts; e.g. 20 lines → [7,14]). If ≤8 lines, null.
                AI_POST metaphor_ids rules (hard): pick 3 to 5 ids from METAPHOR_CATALOG, ordered from best-fit to weakest-fit, that match the emotional core of the story (object-as-feeling metaphor). The FIRST id is the representative/primary metaphor and will be used as the video's opening image — it must be the single best match. The remaining ids will illustrate different beats of the story body, so prefer some variety in mood/tone across the list rather than 5 near-duplicates. Prefer group/tone fit over category label. Required for AI_POST; for HUMAN_POST leave post null (no metaphor).
                """ + "\nKIND=" + req.getKind() + "\nCATEGORY=" + clean(req.getCategory()) + "\nTOPIC=" + clean(req.getTopicHint()) + "\n" + existing +
                grounding +
                "\nMETAPHOR_CATALOG=\n" + MetaphorCatalog.compactCatalog() +
                overusedMetaphorsHint(req.getOverusedMetaphorIds()) +
                "\nPERSONAS=" + json(req.getPersonas()) + "\nLIMITS=" + json(Map.of("topLevel", safe(req.getMaxTopLevel(),14,1,20), "replies", safe(req.getMaxReplies(),10,0,20)));
    }

    /**
     * 최근 과다 사용된 메타포 id 힌트(오케스트레이터가 post_metaphors 집계로 계산해 전달).
     * LLM이 대표 메타포 선택 시 다양성을 갖도록 유도 — 목록이 없으면 빈 문자열.
     */
    private String overusedMetaphorsHint(List<String> overused) {
        if (overused == null || overused.isEmpty()) return "";
        return "\nOVERUSED_METAPHORS=" + json(overused)
                + "\nMETAPHOR_VARIETY_RULE=These ids were used too often recently — avoid them (especially as the first/representative pick) unless clearly the best fit; prefer other METAPHOR_CATALOG ids for variety.";
    }

    private String replyPrompt(HumanReplyBatchRequest req) {
        return """
                Return ONLY {"replies":[{"humanCommentId":1,"personaId":"...","body":"..."}]}.
                For each INPUT human comment, emit 0 to 3 replies. Multiple replies for the same humanCommentId must use different personaIds drawn only from that item's candidateResponders.
                Omitting an interaction (zero rows) is allowed and means no response. Never emit more than 3 rows for one humanCommentId.
                Write short natural Korean. Do not use internal notes, legal verdicts, diagnoses, personal data, or claims of being an AI.
                INPUT=""" + json(req.getItems().stream().map(i -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("humanCommentId", i.getHumanCommentId());
                    row.put("postTitle", clean(i.getPostTitle()));
                    row.put("postBody", clean(i.getPostBody()));
                    row.put("humanBody", clean(i.getHumanBody()));
                    if (!blank(i.getParentBody())) row.put("parentBody", clean(i.getParentBody()));
                    row.put("candidateResponders", slimResponders(i.getCandidateResponders()));
                    return row;
                }).toList());
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
        for (var i : r.getItems()) {
            if (i == null || i.getHumanCommentId() == null || blank(i.getHumanBody())) {
                throw new IllegalArgumentException("humanCommentId and humanBody are required");
            }
            if (i.getCandidateResponders() == null || i.getCandidateResponders().isEmpty()) {
                throw new IllegalArgumentException("candidateResponders are required");
            }
            for (var p : i.getCandidateResponders()) {
                if (p == null || blank(p.getPersonaId())) throw new IllegalArgumentException("candidate personaId is required");
            }
        }
    }

    private void validatePairedPhase1Request(PairedPhase1Request r) {
        if (r == null || r.getPersonas() == null || r.getPersonas().isEmpty()) {
            throw new IllegalArgumentException("personas are required");
        }
        for (var p : r.getPersonas()) if (p == null || blank(p.getPersonaId())) throw new IllegalArgumentException("personaId is required");
    }

    private void validatePairedPhase2Request(PairedPhase2Request r) {
        if (r == null || r.getPersonas() == null || r.getPersonas().isEmpty()) {
            throw new IllegalArgumentException("personas are required");
        }
        for (var p : r.getPersonas()) if (p == null || blank(p.getPersonaId())) throw new IllegalArgumentException("personaId is required");
        if (r.getAuthorPost() == null || blank(r.getAuthorPost().getBody())) {
            throw new IllegalArgumentException("authorPost.body is required");
        }
        if (r.getPublishedTopLevelComments() != null && r.getPublishedTopLevelComments().size() > 8) {
            throw new IllegalArgumentException("publishedTopLevelComments max is 8");
        }
        if (r.getPublishedTopLevelComments() != null) {
            for (var c : r.getPublishedTopLevelComments()) {
                if (c == null || blank(c.getBody())) throw new IllegalArgumentException("published comment body is required");
            }
        }
    }

    private String resolvePlanModel(ThreadPlanRequest r, LlmProvider p) {
        String configured = p == LlmProvider.CODEX ? (r.getKind() == ThreadPlanRequest.Kind.AI_POST ? codexTerra : codexLuna) : (r.getKind() == ThreadPlanRequest.Kind.AI_POST ? claudePostModel : claudeDefault);
        return configuredModel(r.getModel(), configured);
    }
    private String resolveReplyModel(HumanReplyBatchRequest r, LlmProvider p) { return configuredModel(r.getModel(), p == LlmProvider.CODEX ? codexLuna : claudeDefault); }

    /** Paired Call1/Call2 both produce post-grade bodies → stronger post model. */
    private String resolvePairedPostModel(String requested, LlmProvider p) {
        String configured = p == LlmProvider.CODEX ? codexTerra : claudePostModel;
        return configuredModel(requested, configured);
    }
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
    /** Cap matches orchestrator bundle timeout (600s). Raising without env bump is a no-op. */
    private long timeout(Long v) {
        long fallback = defaultTimeoutMs > 0 ? defaultTimeoutMs : 600_000L;
        if (v == null) return fallback;
        return Math.max(1_000L, Math.min(v, 900_000L));
    }
    private static int safe(Integer v, int d, int min, int max) { return Math.max(min, Math.min(v == null ? d : v, max)); }
    private static String text(JsonNode n, String name) { String v = nullableText(n, name); if (v == null) throw new StructuredGenerationException(name + " is required"); return v; }
    /** body/title 추출 시 리터럴 "\n" → 실개행 (PLAN 경로는 OutputSanitizer를 거치지 않음). */
    private static String nullableText(JsonNode n, String name) {
        if (!n.hasNonNull(name)) return null;
        return OutputSanitizer.normalizeLiteralNewlines(n.path(name).asText()).trim();
    }
    private static void validRef(String v) { if (!v.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) throw new StructuredGenerationException("invalid ref"); }
    private static void validText(String v, String field, int min, int max) { if (v.length() < min || v.length() > max || LlmErrorSignature.looksLikeProviderError(v) || META.matcher(v).find() || koreanRatio(v) < .10) throw new StructuredGenerationException("invalid " + field); }
    /** Reject title==body (whitespace-normalized). Prevents one-liner posts used as both fields. */
    static void rejectIdenticalTitleBody(String title, String body) {
        String t = collapseWs(title);
        String b = collapseWs(body);
        if (t.isEmpty() || b.isEmpty()) return;
        if (t.equals(b)) throw new StructuredGenerationException("invalid post.title/body: title must differ from body");
    }

    /** Non-empty newline blocks in body (marketing capture unit). */
    static int countNonEmptyBlocks(String body) {
        if (body == null || body.isBlank()) return 0;
        int n = 0;
        for (String line : body.split("\\R", -1)) {
            if (!line.isBlank()) n++;
        }
        return n;
    }

    /**
     * Accept LLM multi-cut list when body has more than {@link #SHORT_POST_MAX_BLOCKS} blocks.
     * Each part ≤8 blocks; at most {@link #MAX_PARTS_PER_SIDE} parts (≤3 cuts).
     * Legacy single {@code capture_split_after_line} is promoted to a one-element list.
     * Invalid proposals demote to null (do not fail the whole plan).
     */
    static List<Integer> sanitizeCaptureSplits(String body, List<Integer> proposed) {
        int blocks = countNonEmptyBlocks(body);
        if (blocks <= SHORT_POST_MAX_BLOCKS) {
            if (proposed != null && !proposed.isEmpty()) {
                log.debug("Demoting capture_split_after_lines={} — body has {} blocks (≤{})",
                        proposed, blocks, SHORT_POST_MAX_BLOCKS);
            }
            return null;
        }
        if (proposed == null || proposed.isEmpty()) return null;

        List<Integer> cuts = new ArrayList<>();
        int prev = 0;
        for (Integer p : proposed) {
            if (p == null) continue;
            if (cuts.size() >= MAX_PARTS_PER_SIDE - 1) break;
            if (p <= prev || p >= blocks) {
                log.warn("Demoting capture_split_after_lines — cut {} out of range for {} blocks", p, blocks);
                return null;
            }
            int size = p - prev;
            if (size < 1 || size > SHORT_POST_MAX_BLOCKS) {
                log.warn("Demoting capture_split_after_lines — part size {} invalid at cut {}", size, p);
                return null;
            }
            cuts.add(p);
            prev = p;
        }
        if (cuts.isEmpty()) return null;
        int lastSize = blocks - cuts.get(cuts.size() - 1);
        if (lastSize < 1 || lastSize > SHORT_POST_MAX_BLOCKS) {
            // Too many trailing blocks for one last card — keep cuts that fit budget; BE truncates for marketing.
            // If last part alone would exceed 8, demote entirely so BE heuristic applies.
            log.warn("Demoting capture_split_after_lines={} — last part size {} for {} blocks",
                    cuts, lastSize, blocks);
            return null;
        }
        return List.copyOf(cuts);
    }

    /** @deprecated use {@link #sanitizeCaptureSplits} */
    @Deprecated
    static Integer sanitizeCaptureSplit(String body, Integer proposed) {
        List<Integer> list = sanitizeCaptureSplits(body, proposed == null ? null : List.of(proposed));
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    private static List<Integer> readCaptureSplits(JsonNode postNode) {
        JsonNode arr = postNode.get("capture_split_after_lines");
        if (arr == null || arr.isNull()) arr = postNode.get("captureSplitAfterLines");
        if (arr != null && arr.isArray()) {
            List<Integer> out = new ArrayList<>();
            for (JsonNode n : arr) {
                if (n != null && n.isNumber()) out.add(n.asInt());
            }
            if (!out.isEmpty()) return out;
        }
        Integer legacy = readCaptureSplit(postNode);
        return legacy == null ? null : List.of(legacy);
    }

    private static Integer readCaptureSplit(JsonNode postNode) {
        JsonNode n = postNode.get("capture_split_after_line");
        if (n == null || n.isNull()) n = postNode.get("captureSplitAfterLine");
        if (n == null || n.isNull() || !n.isNumber()) return null;
        return n.asInt();
    }

    /**
     * Extract metaphor_ids array from JSON post object. Supports both snake_case and camelCase field names.
     * Returns a list of strings if the array is present and non-empty, null otherwise.
     */
    private static List<String> readMetaphorIds(JsonNode postNode) {
        JsonNode arr = postNode.get("metaphor_ids");
        if (arr == null || arr.isNull()) arr = postNode.get("metaphorIds");
        if (arr != null && arr.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode n : arr) {
                if (n != null && n.isTextual()) {
                    String id = n.asText().trim();
                    if (!id.isEmpty()) out.add(id);
                }
            }
            if (!out.isEmpty()) return out;
        }
        return null;
    }

    /**
     * promo_title must match title when newlines stripped; each non-empty line ≤10;
     * reject orphan-heavy breaks (too many 1-char lines).
     */
    static String sanitizePromoTitle(String title, String proposed) {
        String base = title != null ? title.trim() : "";
        if (base.isEmpty()) return null;
        if (proposed == null || proposed.isBlank()) {
            return wrapPromoLines(base);
        }
        String promo = proposed.replace("\\n", "\n").trim();
        String promoFlat = promo.replace("\n", "").replaceAll("\\s+", "");
        String titleFlat = base.replaceAll("\\s+", "");
        if (!promoFlat.equals(titleFlat)) {
            log.debug("Demoting promo_title — character mismatch with title");
            return wrapPromoLines(base);
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String line : promo.replace("\r\n", "\n").split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.length() > 10) {
                log.debug("Demoting promo_title — line longer than 10");
                return wrapPromoLines(base);
            }
            lines.add(t);
        }
        if (lines.isEmpty()) return wrapPromoLines(base);
        int orphans = 0;
        for (String line : lines) {
            if (line.length() < 2) orphans++;
        }
        if (orphans > 0 && orphans * 4 >= lines.size()) {
            log.debug("Demoting promo_title — too many 1-char lines");
            return wrapPromoLines(base);
        }
        return String.join("\n", lines);
    }

    /**
     * Pack eojeol into ~4–10 char lines (max 10). Avoids 1-char orphan lines.
     */
    static String wrapPromoLines(String title) {
        if (title == null || title.isBlank()) return null;
        String t = title.trim();
        if (t.length() <= 10) return t;
        String[] parts = t.split("\\s+");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (cur.length() == 0) {
                if (part.length() <= 10) cur.append(part);
                else {
                    for (int i = 0; i < part.length(); i += 10) {
                        lines.add(part.substring(i, Math.min(i + 10, part.length())));
                    }
                }
                continue;
            }
            String cand = cur + " " + part;
            if (cand.length() <= 10) {
                cur.setLength(0);
                cur.append(cand);
            } else {
                lines.add(cur.toString());
                cur.setLength(0);
                if (part.length() <= 10) cur.append(part);
                else {
                    for (int i = 0; i < part.length(); i += 10) {
                        lines.add(part.substring(i, Math.min(i + 10, part.length())));
                    }
                }
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        // merge leading/trailing 1-char orphans
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).length() >= 2) continue;
            if (i > 0) {
                String merged = lines.get(i - 1) + " " + lines.get(i);
                if (merged.length() <= 10) {
                    lines.set(i - 1, merged);
                    lines.remove(i);
                    i--;
                    continue;
                }
            }
            if (i + 1 < lines.size()) {
                String merged = lines.get(i) + " " + lines.get(i + 1);
                if (merged.length() <= 10) {
                    lines.set(i + 1, merged);
                    lines.remove(i);
                    i--;
                }
            }
        }
        return String.join("\n", lines);
    }

    private static String collapseWs(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
    private static double koreanRatio(String v) { long k=v.chars().filter(c -> c >= 0xAC00 && c <= 0xD7A3).count(); long letters=v.chars().filter(Character::isLetter).count(); return letters == 0 ? 1 : (double) k / letters; }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String clean(String s) { return s == null ? "" : s.replace('<','＜').replace('>','＞').replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").substring(0, Math.min(s.length(), 5000)); }
    /**
     * Crawled third-party text reaches the prompt through sourceContext. Jackson escapes it
     * structurally, but the project's prompt-injection defense is {@code clean()} (control-char
     * strip + fullwidth &lt;&gt;), so apply it to every string value before serialising.
     */
    private static Map<String, Object> cleanValues(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return raw;
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(k, v instanceof String str ? clean(str) : v));
        return out;
    }

    /**
     * Slim persona serialization for human-reply prompts to prevent token overflow.
     * Include only essential fields for reply generation:
     * - personaId (required for LLM to select)
     * - nickname (helps LLM understand persona)
     * - formality (response style)
     * - voice_type (for community matching)
     * Exclude large fields: example_comments, example_replies, lexicon, writing_quirks, hot_buttons.
     */
    private static List<Map<String, Object>> slimResponders(List<ThreadPlanRequest.Persona> personas) {
        if (personas == null || personas.isEmpty()) return List.of();
        List<Map<String, Object>> slim = new ArrayList<>(personas.size());
        for (ThreadPlanRequest.Persona p : personas) {
            if (p == null || blank(p.getPersonaId())) continue;
            Map<String, Object> responder = new LinkedHashMap<>();
            responder.put("personaId", p.getPersonaId());
            if (!blank(p.getNickname())) responder.put("nickname", p.getNickname());
            if (!blank(p.getFormality())) responder.put("formality", p.getFormality());
            // Include only essential voiceProfile fields, not the full structure
            Map<String, Object> voice = p.getVoiceProfile();
            if (voice != null && !voice.isEmpty()) {
                Map<String, Object> slimVoice = new LinkedHashMap<>();
                // Whitelist essential fields only
                String[] essentialFields = {"voice_type", "age", "gender", "slang_level", "tone", "formality"};
                for (String field : essentialFields) {
                    Object value = voice.get(field);
                    if (value != null && !String.valueOf(value).isBlank()) {
                        slimVoice.put(field, value);
                    }
                }
                if (!slimVoice.isEmpty()) {
                    responder.put("voiceProfile", slimVoice);
                }
            }
            slim.add(responder);
        }
        return slim;
    }

    private static String json(Object value) { try { return JSON.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Cannot serialize structured prompt input", e); } }
}
