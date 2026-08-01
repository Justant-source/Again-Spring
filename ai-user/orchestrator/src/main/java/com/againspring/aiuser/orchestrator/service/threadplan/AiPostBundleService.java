package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.CreatePostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.againspring.aiuser.orchestrator.service.match.PersonaMatcherService;
import com.againspring.aiuser.orchestrator.service.match.RankedPersona;
import com.againspring.aiuser.orchestrator.service.storyprofile.StoryProfileAnalyzer;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PLAN-mode AI post creation boundary.
 *
 * <p>One structured request yields the post and all candidate conversation items.
 * Source-story grounding (example_bank via findSimilar), author voice, reconstruct mode,
 * and anti-self-copy recent bodies are injected here — not empty topicHint alone.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPostBundleService {
    /** Embedded in candidates_json until W1-H adds a dedicated column on ai_scheduled_posts. */
    public static final String SOURCE_PROVENANCE_KEY = "_sourceProvenance";

    private final AiUserGenerationConfigRepository configRepository;
    private final OrchestratorProperties properties;
    private final PersonaRepository personaRepository;
    private final LlmAiUserClient llmClient;
    private final BackendBotClient backendBot;
    private final ContentSafetyGuard safetyGuard;
    private final ThreadPlanService planService;
    private final ThreadPlanGenerationService planGenerationService;
    private final AiScheduledPostRepository scheduledPostRepository;
    private final CandidateScheduleSupport candidateScheduleSupport;
    private final ObjectMapper objectMapper;
    private final PlanPersonaMapper planPersonaMapper;
    private final PlanSourceStoryResolver sourceStoryResolver;
    private final StoryProfileAnalyzer storyProfileAnalyzer;
    private final PersonaMatcherService personaMatcherService;

    /** A PLAN rollout owns post generation even when its workload provider is OFF. */
    public boolean ownsPostGeneration() {
        return properties.isEnabled() && properties.getThreadPlan().isEnabled()
                && configRepository.findById(1)
                .map(c -> !c.isAiUserKillSwitch())
                .orElse(false);
    }

    public Optional<PublishedBundle> generateAndPublish(Persona author, String jwt, String category,
                                                         String topicHint, String correlationId) {
        Bundle bundle = generateBundle(author, category, topicHint, correlationId).orElse(null);
        if (bundle == null) return Optional.empty();

        CreatePostDto.CreatePostDtoBuilder postBuilder = CreatePostDto.builder()
                .userTitle(bundle.content.title()).bodyRaw(bundle.content.body()).category(category)
                .visibility("PUBLIC").jurorCount(0);
        applyProvenance(postBuilder, bundle.source);
        Optional<PostDto> published = backendBot.createPost(jwt, postBuilder.build());
        if (published.isEmpty() || published.get().getId() == null) return Optional.empty();

        PostDto post = published.get();
        try {
            // POST_PUBLISHED outbox delivery will find this revision and must not request another LLM plan.
            AiThreadPlan plan = planService.reservePreGeneratedBundle(post.getId(), 1, Instant.now(),
                    bundle.content.title(), bundle.content.body(), category, bundle.provider, bundle.model);
            planGenerationService.persistResponse(plan.getId(), bundle.response, bundle.castIds);
            planService.markReady(plan.getId());
            planService.activate(plan.getId());
            return Optional.of(new PublishedBundle(post, bundle.content.body(),
                    bundle.source != null ? bundle.source.sourceExampleId() : null));
        } catch (RuntimeException persistenceFailure) {
            // The post was accepted by backend, but it must never cause a second content call.
            log.error("Published AI post {} but could not persist its pre-generated bundle corr={}",
                    post.getId(), correlationId, persistenceFailure);
            return Optional.empty();
        }
    }

    /**
     * Same one-shot structured generation as {@link #generateAndPublish}, but the post is never
     * sent to backend — it is held in {@code ai_scheduled_posts} until {@code scheduledPublishAt}.
     */
    public Optional<AiScheduledPost> generateAndHold(Persona author, String category, String topicHint,
                                                      String correlationId, Instant scheduledPublishAt) {
        Bundle bundle = generateBundle(author, category, topicHint, correlationId).orElse(null);
        if (bundle == null) return Optional.empty();

        candidateScheduleSupport.enrichMissingScheduledAts(bundle.response, scheduledPublishAt);

        // Trace hook for W1-H: embed provenance in candidates JSON until a dedicated column lands.
        if (bundle.source != null && bundle.source.sourceExampleId() != null) {
            bundle.response.put(SOURCE_PROVENANCE_KEY, bundle.source.provenanceForTrace());
        }

        String candidatesJson;
        try {
            candidatesJson = objectMapper.writeValueAsString(bundle.response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializationFailure) {
            log.error("AI post bundle generated but candidates could not be serialized corr={}",
                    correlationId, serializationFailure);
            return Optional.empty();
        }

        AiScheduledPost.AiScheduledPostBuilder rowBuilder = AiScheduledPost.builder()
                .personaId(author.getId())
                .category(category)
                .title(bundle.content.title())
                .body(bundle.content.body())
                .candidatesJson(candidatesJson)
                .scheduledPublishAt(scheduledPublishAt)
                .status(ScheduledPostStatus.SCHEDULED)
                .provider(bundle.provider)
                .model(bundle.model);
        // W1-H may add sourceExampleId on the entity — set when present without owning the migration.
        applyScheduledSourceHook(rowBuilder, bundle.source);
        return Optional.of(scheduledPostRepository.save(rowBuilder.build()));
    }

    /** Shared generation step: grounded structured LLM call, validated post + raw response for replay. */
    private Optional<Bundle> generateBundle(Persona author, String category, String topicHint, String correlationId) {
        if (author == null) {
            log.warn("AI post bundle skipped: author is null corr={}", correlationId);
            return Optional.empty();
        }
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        String provider = config == null ? properties.getThreadPlan().getAiPostProvider()
                : config.getProviderAiPostBundle();
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            log.info("AI post bundle skipped: provider is OFF corr={}", correlationId);
            return Optional.empty();
        }
        String model = properties.getThreadPlan().getAiPostModel();
        int pool = config == null ? 24 : Math.max(1, Math.min(24, config.getCandidatePoolSize()));

        PlanSourceStoryResolver.ResolvedSource source = sourceStoryResolver.resolve(author, category, topicHint);

        // WP3: StoryProfile once per source; reorder comment cast by matcher (author stays personas[0]).
        StoryProfile storyProfile = storyProfileAnalyzer.analyze(
                source.sourceTitle(),
                source.sourceBody(),
                category,
                registerHint(author, source),
                source.sourceExampleId());
        long exampleId = source.sourceExampleId() == null ? 0L : source.sourceExampleId();
        String corr = correlationId == null ? "ai-post-bundle" : correlationId;

        List<Persona> active = personaRepository.findByActiveTrue();
        List<Map<String, Object>> personas = reorderCastByMatcher(
                planPersonaMapper.mapCast(active), author, storyProfile, exampleId, corr);
        if (personas.isEmpty()) {
            log.warn("AI post bundle skipped: no active personas corr={}", correlationId);
            return Optional.empty();
        }
        Set<String> castIds = planPersonaMapper.castIds(personas);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("kind", "AI_POST");
        request.put("provider", provider);
        if (model != null && !model.isBlank()) request.put("model", model);
        request.put("correlationId", correlationId);
        request.put("timeoutMs", properties.getThreadPlan().getBundleTimeoutMs());
        request.put("category", category == null ? "OTHER" : category);
        // Structured source context is the grounding; topicHint is only the derived seed (never empty-only path).
        request.put("topicHint", source.topicSeed());
        request.put("sourceContext", source.sourceContext());
        request.put("reconstructMode", source.reconstructMode());
        if (source.sourceExampleId() != null) request.put("sourceExampleId", source.sourceExampleId());
        if (source.sourceBody() != null) request.put("sourceBody", source.sourceBody());
        if (source.dynamicExamples() != null && !source.dynamicExamples().isBlank()) {
            request.put("dynamicExamples", source.dynamicExamples());
        }
        List<String> recent = PlanSourceStoryResolver.recentOutputsForRequest(source.recentBodies(), 200);
        if (!recent.isEmpty()) request.put("recentOutputs", recent);
        request.put("storyProfile", storyProfileToMap(storyProfile));
        request.put("storySearchDoc", storyProfile.toSearchDocument());
        request.put("author", planPersonaMapper.mapAuthor(author));
        request.put("personas", personas);
        int roots = Math.min(14, pool);
        request.put("maxTopLevel", roots);
        request.put("maxReplies", pool - roots);

        Optional<Map<String, Object>> response = llmClient.generateThreadPlan(request);
        if (response.isEmpty()) return Optional.empty();
        try {
            PostContent postContent = readAndValidatePost(response.get());
            validateCast(response.get(), castIds);
            return Optional.of(new Bundle(response.get(), postContent, provider, model, castIds, source));
        } catch (IllegalArgumentException invalid) {
            log.warn("AI post bundle rejected corr={}: {}", correlationId, invalid.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    static void validateCast(Map<String, Object> response, Set<String> castIds) {
        Object rawItems = response.get("items");
        if (!(rawItems instanceof List<?> rows)) return;
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> row)) continue;
            Object pid = row.get("personaId");
            if (pid == null) continue;
            String id = String.valueOf(pid).trim();
            if (!id.isEmpty() && !castIds.contains(id)) {
                throw new IllegalArgumentException("personaId not in requested cast: " + id);
            }
        }
    }

    private static void applyProvenance(CreatePostDto.CreatePostDtoBuilder b,
                                        PlanSourceStoryResolver.ResolvedSource source) {
        if (source == null || !source.reconstructMode() || source.sourceExampleId() == null) return;
        b.sourceExampleId(source.sourceExampleId())
                .sourceCommunity(source.sourceCommunity())
                .sourceUrl(source.sourceUrl())
                .sourceOriginalTitle(source.sourceTitle())
                .sourceOriginalBody(PlanSourceStoryResolver.truncate(source.sourceBody(), 2000));
    }

    /**
     * Soft hook for W1-H: if AiScheduledPost gains {@code sourceExampleId}, populate it.
     * Until then provenance lives under {@link #SOURCE_PROVENANCE_KEY} in candidates JSON.
     */
    private static void applyScheduledSourceHook(AiScheduledPost.AiScheduledPostBuilder rowBuilder,
                                                 PlanSourceStoryResolver.ResolvedSource source) {
        if (source == null || source.sourceExampleId() == null) return;
        try {
            var method = AiScheduledPost.AiScheduledPostBuilder.class.getMethod("sourceExampleId", Long.class);
            method.invoke(rowBuilder, source.sourceExampleId());
        } catch (ReflectiveOperationException ignored) {
            // Column not yet present (W1-H). Provenance is in candidates JSON.
        }
    }

    /**
     * Author first, then matcher-ranked commenters, then remaining cast (stable).
     * Matcher failures degrade to author-first original order.
     */
    List<Map<String, Object>> reorderCastByMatcher(
            List<Map<String, Object>> cast,
            Persona author,
            StoryProfile profile,
            long sourceExampleId,
            String correlationId) {
        List<Map<String, Object>> base = cast == null ? List.of() : new ArrayList<>(cast);
        Map<String, Object> authorEntry = planPersonaMapper.mapAuthor(author);
        base.removeIf(m -> author.getId().equals(String.valueOf(m.getOrDefault("personaId", ""))));

        List<String> rankedIds = List.of();
        try {
            List<RankedPersona> ranked = personaMatcherService.matchCommenters(
                    profile, Math.min(60, Math.max(1, base.size())), sourceExampleId, correlationId + "-cast");
            rankedIds = ranked.stream().map(RankedPersona::personaId).toList();
        } catch (Exception e) {
            log.debug("cast matcher skipped corr={}: {}", correlationId, e.getMessage());
        }

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> out = new ArrayList<>(base.size() + 1);
        out.add(authorEntry);
        seen.add(author.getId());

        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> m : base) {
            String id = String.valueOf(m.getOrDefault("personaId", ""));
            if (!id.isBlank()) byId.put(id, m);
        }
        for (String id : rankedIds) {
            if (seen.contains(id)) continue;
            Map<String, Object> row = byId.get(id);
            if (row != null) {
                out.add(row);
                seen.add(id);
            }
        }
        for (Map<String, Object> m : base) {
            String id = String.valueOf(m.getOrDefault("personaId", ""));
            if (id.isBlank() || seen.contains(id)) continue;
            out.add(m);
            seen.add(id);
        }
        return out;
    }

    static Map<String, Object> storyProfileToMap(StoryProfile p) {
        if (p == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("centralConflict", p.centralConflict());
        m.put("category", p.category());
        m.put("topics", p.topics());
        m.put("explicitIdentity", p.explicitIdentity());
        m.put("lifeContext", p.lifeContext());
        m.put("valueAxis", p.valueAxis());
        m.put("sourceRegister", p.sourceRegister());
        m.put("replyAffordances", p.replyAffordances());
        m.put("searchDoc", p.toSearchDocument());
        return m;
    }

    /** Prefer example source community; else persona voice_type (NATEPAN|BLIND). */
    static String registerHint(Persona author, PlanSourceStoryResolver.ResolvedSource source) {
        if (source != null && source.sourceCommunity() != null && !source.sourceCommunity().isBlank()) {
            return source.sourceCommunity();
        }
        if (author != null && author.getVoiceProfile() != null) {
            Object vt = author.getVoiceProfile().get("voice_type");
            if (vt != null && !String.valueOf(vt).isBlank()) return String.valueOf(vt);
        }
        return "NATEPAN";
    }

    private record Bundle(Map<String, Object> response, PostContent content, String provider, String model,
                          Set<String> castIds, PlanSourceStoryResolver.ResolvedSource source) { }

    @SuppressWarnings("unchecked")
    private PostContent readAndValidatePost(Map<String, Object> response) {
        if (!(response.get("post") instanceof Map<?, ?> raw)) throw new IllegalArgumentException("missing post");
        String title = text(raw.get("title"));
        String body = text(raw.get("body"));
        if (title.isBlank() || title.length() > 200 || body.isBlank()) throw new IllegalArgumentException("invalid post fields");
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(body, ContentSafetyGuard.ContentType.POST);
        if (!guard.passed()) throw new IllegalArgumentException("unsafe post: " + guard.reason());
        return new PostContent(title, body);
    }

    private static String text(Object value) {
        if (value == null) return "";
        return LiteralNewlineNormalizer.normalize(String.valueOf(value)).trim();
    }

    private record PostContent(String title, String body) { }

    public record PublishedBundle(PostDto post, String body, Long sourceExampleId) {
        public PublishedBundle(PostDto post, String body) {
            this(post, body, null);
        }
    }
}
