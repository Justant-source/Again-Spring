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
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PLAN-mode AI post creation boundary.
 *
 * <p>The legacy executor generates a post first and lets a later outbox event create a
 * comment plan. This service deliberately replaces that sequence only when PLAN mode is
 * enabled: one structured request yields the post and all candidate conversation items,
 * then the validated post is published and its already-generated candidates are persisted.
 * The resulting ACTIVE plan makes the subsequent POST_PUBLISHED outbox delivery idempotent.
 * No fallback to the legacy per-post endpoint is allowed from this path.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPostBundleService {
    private final AiUserGenerationConfigRepository configRepository;
    private final OrchestratorProperties properties;
    private final PersonaRepository personaRepository;
    private final LlmAiUserClient llmClient;
    private final BackendBotClient backendBot;
    private final ContentSafetyGuard safetyGuard;
    private final ThreadPlanService planService;
    private final ThreadPlanGenerationService planGenerationService;
    private final AiScheduledPostRepository scheduledPostRepository;
    private final ObjectMapper objectMapper;

    /** A PLAN rollout owns post generation even when its workload provider is OFF. */
    public boolean ownsPostGeneration() {
        return properties.isEnabled() && properties.getThreadPlan().isEnabled()
                && configRepository.findById(1)
                .map(c -> !c.isAiUserKillSwitch())
                .orElse(false);
    }

    public Optional<PublishedBundle> generateAndPublish(Persona author, String jwt, String category,
                                                         String topicHint, String correlationId) {
        Bundle bundle = generateBundle(category, topicHint, correlationId).orElse(null);
        if (bundle == null) return Optional.empty();

        Optional<PostDto> published = backendBot.createPost(jwt, CreatePostDto.builder()
                .userTitle(bundle.content.title()).bodyRaw(bundle.content.body()).category(category)
                .visibility("PUBLIC").jurorCount(0).build());
        if (published.isEmpty() || published.get().getId() == null) return Optional.empty();

        PostDto post = published.get();
        try {
            // POST_PUBLISHED outbox delivery will find this revision and must not request another LLM plan.
            AiThreadPlan plan = planService.reservePreGeneratedBundle(post.getId(), 1, Instant.now(),
                    bundle.content.title(), bundle.content.body(), category, bundle.provider, bundle.model);
            planGenerationService.persistResponse(plan.getId(), bundle.response);
            planService.markReady(plan.getId());
            planService.activate(plan.getId());
            return Optional.of(new PublishedBundle(post, bundle.content.body()));
        } catch (RuntimeException persistenceFailure) {
            // The post was accepted by backend, but it must never cause a second content call.
            // The durable outbox creates a REQUESTED plan which can be retried manually if this write failed.
            log.error("Published AI post {} but could not persist its pre-generated bundle corr={}",
                    post.getId(), correlationId, persistenceFailure);
            return Optional.empty();
        }
    }

    /**
     * Same one-shot structured generation as {@link #generateAndPublish}, but the post is never
     * sent to backend — it is held in {@code ai_scheduled_posts} until {@code scheduledPublishAt}.
     * {@link ScheduledPostPublisher} creates the real post (and replays these candidates into a
     * thread plan) when the slot arrives. Used by the nightly batch so "generate at 3am" and
     * "appear in the feed" are no longer the same moment.
     */
    public Optional<AiScheduledPost> generateAndHold(Persona author, String category, String topicHint,
                                                      String correlationId, Instant scheduledPublishAt) {
        Bundle bundle = generateBundle(category, topicHint, correlationId).orElse(null);
        if (bundle == null) return Optional.empty();

        String candidatesJson;
        try {
            candidatesJson = objectMapper.writeValueAsString(bundle.response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException serializationFailure) {
            log.error("AI post bundle generated but candidates could not be serialized corr={}",
                    correlationId, serializationFailure);
            return Optional.empty();
        }

        AiScheduledPost row = AiScheduledPost.builder()
                .personaId(author.getId())
                .category(category)
                .title(bundle.content.title())
                .body(bundle.content.body())
                .candidatesJson(candidatesJson)
                .scheduledPublishAt(scheduledPublishAt)
                .status(ScheduledPostStatus.SCHEDULED)
                .provider(bundle.provider)
                .model(bundle.model)
                .build();
        return Optional.of(scheduledPostRepository.save(row));
    }

    /** Shared generation step: one structured LLM call, validated post + raw response for replay. */
    private Optional<Bundle> generateBundle(String category, String topicHint, String correlationId) {
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        String provider = config == null ? properties.getThreadPlan().getAiPostProvider()
                : config.getProviderAiPostBundle();
        if (provider == null || provider.isBlank() || "OFF".equalsIgnoreCase(provider)) {
            log.info("AI post bundle skipped: provider is OFF corr={}", correlationId);
            return Optional.empty();
        }
        String model = properties.getThreadPlan().getAiPostModel();
        int pool = config == null ? 24 : Math.max(1, Math.min(24, config.getCandidatePoolSize()));
        List<Map<String, Object>> personas = personaRepository.findByActiveTrue().stream().limit(24)
                .<Map<String, Object>>map(p -> Map.of("personaId", p.getId(), "nickname", p.getId(),
                        "voiceProfile", String.valueOf(p.getVoiceProfile()), "formality", "neutral"))
                .toList();
        if (personas.isEmpty()) {
            log.warn("AI post bundle skipped: no active personas corr={}", correlationId);
            return Optional.empty();
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("kind", "AI_POST");
        request.put("provider", provider);
        if (model != null && !model.isBlank()) request.put("model", model);
        request.put("correlationId", correlationId);
        request.put("timeoutMs", properties.getThreadPlan().getBundleTimeoutMs());
        request.put("category", category == null ? "OTHER" : category);
        request.put("topicHint", topicHint == null ? "" : topicHint);
        request.put("personas", personas);
        int roots = Math.min(14, pool);
        request.put("maxTopLevel", roots);
        request.put("maxReplies", pool - roots);

        // Do not retry here. The AI-post bundle contract is one structured invocation per post.
        Optional<Map<String, Object>> response = llmClient.generateThreadPlan(request);
        if (response.isEmpty()) return Optional.empty();
        try {
            PostContent postContent = readAndValidatePost(response.get());
            return Optional.of(new Bundle(response.get(), postContent, provider, model));
        } catch (IllegalArgumentException invalid) {
            log.warn("AI post bundle rejected corr={}: {}", correlationId, invalid.getMessage());
            return Optional.empty();
        }
    }

    private record Bundle(Map<String, Object> response, PostContent content, String provider, String model) { }

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

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private record PostContent(String title, String body) { }
    public record PublishedBundle(PostDto post, String body) { }
}
