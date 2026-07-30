package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Optional;

/** Due-only publisher. It never generates text and keeps a failed backend write visible for manual retry. */
@Slf4j @Service @RequiredArgsConstructor
public class ThreadPlanPublisher {
    private final ThreadPlanItemLeaseService leases;
    private final PersonaRepository personas;
    private final AiThreadPlanItemRepository items;
    private final BotTokenCache tokens;
    private final BackendBotClient backend;
    private final JdbcTemplate jdbcTemplate;
    private final OrchestratorProperties properties;
    private final AiUserGenerationConfigRepository configRepository;

    public void publishDue() {
        if (!properties.isEnabled() || !properties.getThreadPlan().isEnabled() || !properties.getThreadPlan().isPublisherEnabled()
                || configRepository.findById(1).map(c -> !"PLAN".equalsIgnoreCase(c.getSchedulerMode()) || c.isAiUserKillSwitch() || c.isScheduleExecutionPaused()).orElse(true)) return;
        String worker = "thread-publisher";
        for (AiThreadPlanItem item : leases.claimDue(worker, properties.getThreadPlan().getPublishBatchSize(), Duration.ofMinutes(5), Instant.now())) publish(item, worker);
    }
    private void publish(AiThreadPlanItem item, String worker) {
        if (quietKst() && item.getParentItemId() == null) { leases.defer(item.getId(), worker, nextActiveKst(), "QUIET_HOURS"); return; }
        try {
            Long parent = parentId(item);
            if (item.getParentItemId() != null && parent == null) { leases.releaseFailed(item.getId(), worker, "PARENT_NOT_POSTED", true); return; }
            Persona persona = personas.findById(item.getPersonaId()).orElseThrow();
            String email = jdbcTemplate.queryForObject("select email from users where id = ?", String.class, persona.getId());
            Optional<String> jwt = tokens.getToken(persona.getId(), email, properties.getBotPassword());
            if (jwt.isEmpty()) { leases.releaseFailed(item.getId(), worker, "AUTH_FAILED", false); return; }
            Optional<String> posted = backend.addCommentReturningId(
                    jwt.get(), item.getTargetPostId(), item.getBody(), parent, item.getIdempotencyKey());
            if (posted.isPresent()) leases.completePosted(item.getId(), worker, posted.get());
            else leases.releaseFailed(item.getId(), worker, "BACKEND_WRITE_FAILED", false);
        } catch (Exception e) {
            log.warn("Plan item publish failed id={}: {}", item.getId(), e.getMessage());
            leases.releaseFailed(item.getId(), worker, "PUBLISH_EXCEPTION", false);
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
