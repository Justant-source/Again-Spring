package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.*;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemStatus;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanItemType;
import com.againspring.aiuser.orchestrator.repository.*;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.*; import java.util.*;

/** Bounded 30-minute human interaction batch: 50 comments across at most 10 posts, one retry only. */
@Slf4j @Service @RequiredArgsConstructor
public class HumanReplyBatchService {
    private final HumanInteractionInboxService inbox;
    private final AiThreadPlanRepository plans;
    private final AiThreadPlanItemRepository planItems;
    private final LlmAiUserClient llm;
    private final ContentSafetyGuard guard;
    private final OrchestratorProperties props;
    private final AiUserGenerationConfigRepository configRepository;
    public void run() {
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        if (!props.isEnabled() || !props.getThreadPlan().isEnabled() || !props.getThreadPlan().isHumanReplyBatchEnabled() || config == null
                || !"PLAN".equalsIgnoreCase(config.getSchedulerMode()) || config.isAiUserKillSwitch() || "OFF".equalsIgnoreCase(config.getProviderHumanInteraction())) return;
        String worker = "human-reply-batch"; Instant now = Instant.now();
        int maxComments = config.getHumanBatchMaxInteractions() > 0 ? config.getHumanBatchMaxInteractions() : props.getThreadPlan().getHumanReplyMaxComments();
        int maxPosts = config.getHumanBatchMaxPosts() > 0 ? config.getHumanBatchMaxPosts() : props.getThreadPlan().getHumanReplyMaxPosts();
        List<AiHumanInteractionInbox> claimed = inbox.claimPending(worker, maxComments, Duration.ofMinutes(29), now);
        List<AiHumanInteractionInbox> selected = new ArrayList<>(); Set<String> posts = new HashSet<>();
        for (AiHumanInteractionInbox entry : claimed) if (posts.contains(entry.getPostId()) || posts.size() < maxPosts) { selected.add(entry); posts.add(entry.getPostId()); }
        claimed.stream().filter(e -> !selected.contains(e)).forEach(e -> inbox.release(e.getId(), worker));
        if (selected.isEmpty()) return;
        Map<String,Object> request = new LinkedHashMap<>(); request.put("provider", config.getProviderHumanInteraction());
        if (!props.getThreadPlan().getHumanPlanModel().isBlank()) request.put("model", props.getThreadPlan().getHumanPlanModel());
        request.put("correlationId", "human-replies-" + now.toEpochMilli());
        request.put("items", selected.stream().map(e -> Map.of("postId", Long.valueOf(e.getPostId()), "humanCommentId", Long.valueOf(e.getSourceCommentId()),
                "parentCommentId", e.getParentCommentId() == null ? 0L : Long.valueOf(e.getParentCommentId()), "postTitle", "", "postBody", "", "humanBody", "", "responder", Map.of())).toList());
        Optional<Map<String,Object>> response = llm.generateHumanReplies(request); if (response.isEmpty()) response = llm.generateHumanReplies(request);
        if (response.isEmpty()) { selected.forEach(e -> inbox.release(e.getId(), worker)); return; }
        persist(worker, selected, response.get(), now);
    }
    @Transactional @SuppressWarnings("unchecked")
    void persist(String worker, List<AiHumanInteractionInbox> selected, Map<String,Object> response, Instant now) {
        Map<String,AiHumanInteractionInbox> byComment = new HashMap<>(); selected.forEach(e -> byComment.put(e.getSourceCommentId(), e));
        Object raw = response.get("replies"); if (!(raw instanceof List<?> replies)) { selected.forEach(e -> inbox.release(e.getId(), worker)); return; }
        Set<String> answered = new HashSet<>();
        for (Object value : replies) if (value instanceof Map<?,?> row) {
            String comment = String.valueOf(row.get("humanCommentId")); AiHumanInteractionInbox entry = byComment.get(comment);
            String body = row.get("body") == null ? "" : String.valueOf(row.get("body")).trim(); String persona = row.get("personaId") == null ? "" : String.valueOf(row.get("personaId"));
            if (entry == null || body.isBlank() || persona.isBlank() || !guard.check(body, ContentSafetyGuard.ContentType.COMMENT).passed()) continue;
            AiThreadPlan plan = plans.findTopByPostIdOrderByPostRevisionDesc(entry.getPostId()).orElse(null); if (plan == null || now.isAfter(plan.getAbsoluteExpiresAt())) continue;
            AiThreadPlanItem item = planItems.save(AiThreadPlanItem.builder().planId(plan.getId()).itemType(ThreadPlanItemType.REPLY).status(ThreadPlanItemStatus.SCHEDULED)
                    .sequenceNo(10000 + answered.size()).personaId(persona).targetPostId(entry.getPostId()).targetCommentId(entry.getSourceCommentId()).body(body)
                    .scheduledAt(now.plusSeconds(15 * 60)).notBefore(now.plusSeconds(15 * 60)).idempotencyKey("human-reply:" + entry.getSourceCommentId()).build());
            inbox.markResponded(entry.getId(), worker, item.getId()); answered.add(comment);
        }
        selected.stream().filter(e -> !answered.contains(e.getSourceCommentId())).forEach(e -> inbox.release(e.getId(), worker));
    }
}
