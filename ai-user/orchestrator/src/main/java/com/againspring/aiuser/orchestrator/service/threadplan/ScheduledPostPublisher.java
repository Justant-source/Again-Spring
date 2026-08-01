package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.CreatePostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Creates the real post when a held {@link AiScheduledPost}'s slot arrives, then replays its
 * pre-generated comment/reply candidates into a thread plan (same replay path as
 * {@code AiPostBundleService.generateAndPublish} — no second LLM call). This is what turns
 * "generated at 3am" into "appeared in the feed at whatever hour the curve picked," which the
 * nightly batch alone cannot do since it only writes the {@code ai_scheduled_posts} row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPostPublisher {
    private static final String WORKER = "scheduled-post-publisher";

    private final ScheduledPostLeaseService leases;
    private final PersonaRepository personas;
    private final BotTokenCache tokens;
    private final BackendBotClient backend;
    private final JdbcTemplate jdbcTemplate;
    private final OrchestratorProperties properties;
    private final ThreadPlanService planService;
    private final ThreadPlanGenerationService planGenerationService;
    private final ObjectMapper objectMapper;

    public void publishDue() {
        if (!properties.isEnabled() || !properties.getThreadPlan().isEnabled()
                || !properties.getThreadPlan().isScheduledPostPublisherEnabled()) return;
        int batchSize = properties.getThreadPlan().getScheduledPostPublishBatchSize();
        for (AiScheduledPost row : leases.claimDue(WORKER, batchSize, Duration.ofMinutes(5), Instant.now())) {
            publish(row);
        }
    }

    private void publish(AiScheduledPost row) {
        try {
            Persona author = personas.findById(row.getPersonaId()).orElse(null);
            if (author == null) { leases.releaseFailed(row.getId(), WORKER, "PERSONA_NOT_FOUND", false); return; }
            String email = jdbcTemplate.queryForObject("select email from users where id = ?", String.class, author.getId());
            Optional<String> jwt = tokens.getToken(author.getId(), email, properties.getBotPassword());
            if (jwt.isEmpty()) { leases.releaseFailed(row.getId(), WORKER, "AUTH_FAILED", true); return; }

            Optional<PostDto> published = backend.createPost(jwt.get(), CreatePostDto.builder()
                    .userTitle(row.getTitle())
                    .bodyRaw(LiteralNewlineNormalizer.normalize(row.getBody()))
                    .category(row.getCategory())
                    .visibility("PUBLIC").jurorCount(0).build());
            if (published.isEmpty() || published.get().getId() == null) {
                leases.releaseFailed(row.getId(), WORKER, "BACKEND_WRITE_FAILED", true);
                return;
            }

            PostDto post = published.get();
            replayCandidates(row, post);
            leases.completePosted(row.getId(), WORKER, post.getId());
        } catch (Exception e) {
            log.warn("Scheduled post publish failed id={}: {}", row.getId(), e.getMessage());
            leases.releaseFailed(row.getId(), WORKER, "PUBLISH_EXCEPTION", row.getAttemptCount() < 3);
        }
    }

    @SuppressWarnings("unchecked")
    private void replayCandidates(AiScheduledPost row, PostDto post) {
        if (row.getCandidatesJson() == null || row.getCandidatesJson().isBlank()) return;
        try {
            Map<String, Object> response = objectMapper.readValue(row.getCandidatesJson(), new TypeReference<>() { });
            AiThreadPlan plan = planService.reservePreGeneratedBundle(post.getId(), 1, Instant.now(),
                    row.getTitle(), row.getBody(), row.getCategory(), row.getProvider(), row.getModel());
            planGenerationService.persistResponse(plan.getId(), response);
            planService.markReady(plan.getId());
            planService.activate(plan.getId());
        } catch (Exception replayFailure) {
            // The post is already live — losing its comment plan must never roll back the post itself.
            // The durable outbox still fires POST_PUBLISHED, which creates a REQUESTED plan as a fallback.
            log.error("Published scheduled post {} but could not replay its candidates id={}",
                    post.getId(), row.getId(), replayFailure);
        }
    }

}
