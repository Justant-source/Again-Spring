package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/** Due-only publisher. It never generates text and keeps a failed backend write visible for manual retry. */
@Slf4j @Service @RequiredArgsConstructor
public class ThreadPlanPublisher {
    private final ThreadPlanItemLeaseService leases;
    private final PersonaRepository personas;
    private final AiThreadPlanItemRepository items;
    private final AiThreadPlanRepository plans;
    private final BotTokenCache tokens;
    private final BackendBotClient backend;
    private final JdbcTemplate jdbcTemplate;
    private final OrchestratorProperties properties;
    private final AiUserGenerationConfigRepository configRepository;

    /** Threshold above which a scheduledAt offset is considered "stale" (publisher lag after restart). */
    private static final Duration STAMPEDE_THRESHOLD = Duration.ofMinutes(30);
    /** Minimum remaining time before expiry; if less, publish immediately instead of redistributing. */
    private static final Duration MIN_REMAINING_FOR_REDISTRIBUTE = Duration.ofMinutes(15);

    public void publishDue() {
        if (!properties.isEnabled() || !properties.getThreadPlan().isEnabled() || !properties.getThreadPlan().isPublisherEnabled()
                || configRepository.findById(1).map(c -> c.isAiUserKillSwitch() || c.isScheduleExecutionPaused()).orElse(true)) return;
        String worker = "thread-publisher";
        Instant now = Instant.now();
        for (AiThreadPlanItem item : leases.claimDue(worker, properties.getThreadPlan().getPublishBatchSize(), Duration.ofMinutes(5), now)) {
            handleStampedeRedistribution(item, worker, now);
        }
    }

    /**
     * Detects if an item's scheduledAt is stale (more than STAMPEDE_THRESHOLD in the past),
     * and if so, redistributes it across the remaining time window instead of publishing immediately.
     * Non-stale items are published normally.
     */
    private void handleStampedeRedistribution(AiThreadPlanItem item, String worker, Instant now) {
        if (item.getScheduledAt() == null) {
            publish(item, worker);
            return;
        }

        long staleDurationSeconds = Duration.between(item.getScheduledAt(), now).getSeconds();
        if (staleDurationSeconds <= STAMPEDE_THRESHOLD.getSeconds()) {
            // Not stale, publish normally
            publish(item, worker);
            return;
        }

        // Item is stale: attempt redistribution
        try {
            AiThreadPlan plan = plans.findById(item.getPlanId()).orElse(null);
            if (plan == null) {
                log.warn("Plan not found for stale item id={} planId={}", item.getId(), item.getPlanId());
                publish(item, worker);
                return;
            }

            long remainingSeconds = Duration.between(now, plan.getAbsoluteExpiresAt()).getSeconds();
            if (remainingSeconds <= 0) {
                // Plan has already expired; publish immediately to fail/expire normally
                publish(item, worker);
                return;
            }

            if (remainingSeconds < MIN_REMAINING_FOR_REDISTRIBUTE.getSeconds()) {
                // Less than 15 minutes until expiry; no point redistributing, publish now
                log.debug("Stale item id={} has < 15 min until expiry ({}s remaining), publishing immediately",
                        item.getId(), remainingSeconds);
                publish(item, worker);
                return;
            }

            // Compute a new slot via ActivityCurve within the remaining window
            Instant redistributedAt = sampleNewSlot(now, plan.getAbsoluteExpiresAt());
            log.info("Stampede: redistributing stale item id={} from {} to {} ({}s late, {}s remaining)",
                    item.getId(), item.getScheduledAt(), redistributedAt, staleDurationSeconds, remainingSeconds);
            leases.defer(item.getId(), worker, redistributedAt, "STAMPEDE_REDISTRIBUTE");
        } catch (Exception e) {
            log.warn("Error handling stampede redistribution for item id={}: {}", item.getId(), e.getMessage());
            publish(item, worker);
        }
    }

    /**
     * Samples a single publish slot within [now, expiry), weighted by activity curve.
     * Falls back to a simple offset if sampling fails (e.g., window too narrow).
     */
    private Instant sampleNewSlot(Instant now, Instant expiresAt) {
        Duration window = Duration.between(now, expiresAt);
        if (window.getSeconds() < 60) {
            // Window too narrow for curve sampling; use expiry - 1 minute as a reasonable fallback
            return expiresAt.minusSeconds(60);
        }

        try {
            List<Instant> sampled = ActivityCurve.sampleFutureInstants(
                    now, expiresAt, 1,
                    properties.getThreadPlan().getKstHourlyHumanWeights(),
                    Duration.ofMinutes(1), // minSpacing — ignored for a single sample, but must be present
                    new Random());
            return sampled.isEmpty() ? expiresAt.minusSeconds(60) : sampled.get(0);
        } catch (IllegalArgumentException e) {
            // Window is too narrow even for 1 sample at 1-minute spacing
            log.debug("sampleNewSlot: fallback to expiry-1m due to narrow window: {}", e.getMessage());
            return expiresAt.minusSeconds(60);
        }
    }
    private void publish(AiThreadPlanItem item, String worker) {
        if (quietKst() && item.getParentItemId() == null) { leases.defer(item.getId(), worker, nextActiveKst(), "QUIET_HOURS"); return; }
        try {
            if (isForbiddenStorySideBystander(item)) {
                log.warn("Plan item {} cancelled: story-side persona must not post bystander comments", item.getId());
                leases.releaseFailed(item.getId(), worker, "STORY_PERSONA_COMMENT", false);
                return;
            }
            Long parent = parentId(item);
            if (item.getParentItemId() != null && parent == null) { leases.releaseFailed(item.getId(), worker, "PARENT_NOT_POSTED", true); return; }
            Persona persona = personas.findById(item.getPersonaId()).orElseThrow();
            String email = jdbcTemplate.queryForObject("select email from users where id = ?", String.class, persona.getId());
            Optional<String> jwt = tokens.getToken(persona.getId(), email, properties.getBotPassword());
            if (jwt.isEmpty()) { leases.releaseFailed(item.getId(), worker, "AUTH_FAILED", false); return; }
            Optional<String> posted = backend.addCommentReturningId(
                    jwt.get(), item.getTargetPostId(),
                    LiteralNewlineNormalizer.normalize(item.getBody()),
                    parent, item.getIdempotencyKey());
            if (posted.isPresent()) leases.completePosted(item.getId(), worker, posted.get());
            else leases.releaseFailed(item.getId(), worker, "BACKEND_WRITE_FAILED", false);
        } catch (Exception e) {
            log.warn("Plan item publish failed id={}: {}", item.getId(), e.getMessage());
            leases.releaseFailed(item.getId(), worker, "PUBLISH_EXCEPTION", false);
        }
    }

    /**
     * Human-reply batch sets {@code humanAuthorId} and may intentionally use the post author
     * persona. Generated bystander plans must never use author/partner as the commenter.
     */
    private boolean isForbiddenStorySideBystander(AiThreadPlanItem item) {
        if (item.getHumanAuthorId() != null && !item.getHumanAuthorId().isBlank()) return false;
        if (item.getPersonaId() == null || item.getPersonaId().isBlank()
                || item.getTargetPostId() == null || item.getTargetPostId().isBlank()) {
            return false;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT author_id, partner_user_id FROM posts WHERE id = ? AND deleted_at IS NULL",
                    item.getTargetPostId());
            if (rows.isEmpty()) return false;
            Map<String, Object> row = rows.get(0);
            String author = row.get("author_id") == null ? "" : String.valueOf(row.get("author_id"));
            String partner = row.get("partner_user_id") == null ? "" : String.valueOf(row.get("partner_user_id"));
            String persona = item.getPersonaId();
            return persona.equals(author) || (!partner.isBlank() && !"null".equalsIgnoreCase(partner) && persona.equals(partner));
        } catch (Exception e) {
            log.debug("story-side check skipped item={}: {}", item.getId(), e.getMessage());
            return false;
        }
    }
    private Long parentId(AiThreadPlanItem item) {
        if (item.getTargetCommentId() != null) return Long.valueOf(item.getTargetCommentId());
        if (item.getParentItemId() == null) return null;
        return items.findById(item.getParentItemId()).map(AiThreadPlanItem::getPostedTargetId)
                .filter(value -> value != null && value.matches("\\d+"))
                .map(Long::valueOf).orElse(null);
    }
    private boolean quietKst() { int hour = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("Asia/Seoul")).getHour(); return hour >= 2 && hour < 6; }
    private Instant nextActiveKst() {
        ZoneId kst = ZoneId.of("Asia/Seoul"); ZonedDateTime now = ZonedDateTime.now(kst);
        return now.withHour(6).withMinute(5).withSecond(0).withNano(0).toInstant();
    }
}
