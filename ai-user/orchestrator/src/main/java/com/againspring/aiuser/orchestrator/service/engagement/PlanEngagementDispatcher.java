package com.againspring.aiuser.orchestrator.service.engagement;

import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.engine.PlannedAction;
import com.againspring.aiuser.orchestrator.engine.ViewDispatcher;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.service.engagement.EngagementSnapshotReader.CommentRow;
import com.againspring.aiuser.orchestrator.service.engagement.EngagementSnapshotReader.PostSnapshot;
import com.againspring.aiuser.orchestrator.task.ActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Converges comment/reply like counts and post view counts toward the formula targets in
 * {@code docs/ai-user/thread-planning.md} §LLM 없는 engagement, entirely independently of
 * {@code BehaviorEngine}'s (now-removed) tick loop.
 *
 * <p>Deliberately re-evaluated on a timer rather than triggered at publish time: a comment's
 * correct like target depends on the post's view count and its own reply count, neither of
 * which are known the instant it publishes. Because {@link #reconcile} computes
 * {@code deficit = target - current} and only ever applies the shortfall, backfilling old
 * content and steady-state operation are the same call with a wider {@code lookbackDays} —
 * there is no separate backfill code path to drift out of sync with this one.
 *
 * <p><b>Scope: views + comment/reply likes only.</b> Post-level likes and votes are owned by
 * {@link com.againspring.aiuser.orchestrator.service.threadplan.VoteLikeBatchService}
 * (target-count based, via {@code provider_vote_like}) — this class deliberately does not
 * dispatch {@code PlannedAction.like(post)} to avoid two independent mechanisms converging on
 * the same post-like counter. {@link EngagementTargetCalculator#postLikeTarget} still exists
 * for completeness against the documented formula, but nothing here calls it.
 *
 * <p>Does not touch {@code ai_user_runtime.daily_global_cap} — that counter is written
 * exclusively by the (now-removed) LEGACY tick loop, and sharing it would mean engagement
 * silently stops the moment that cap is reached by unrelated activity, which is exactly the
 * failure mode this class exists to fix. Volume is bounded per-run instead
 * ({@code maxPostsPerRun}, {@code maxLikeCallsPerRun}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanEngagementDispatcher {

    private final EngagementSnapshotReader reader;
    private final ViewDispatcher viewDispatcher;
    private final PersonaRepository personaRepository;
    private final ActionExecutor actionExecutor;
    private final OrchestratorProperties properties;
    private final AiUserGenerationConfigRepository configRepository;

    /** Called by {@code PlanEngagementScheduler} on its cron. Applies configured defaults. */
    public void reconcileDue() {
        if (!properties.isEnabled() || !properties.getThreadPlan().isEnabled()
                || !properties.getThreadPlan().getEngagement().isEnabled()) {
            return;
        }
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        if (config == null || config.isAiUserKillSwitch() || config.isScheduleExecutionPaused()) {
            return;
        }
        OrchestratorProperties.Engagement e = properties.getThreadPlan().getEngagement();
        EngagementResult result = reconcile(e.getLookbackDays(), e.getMaxPostsPerRun(), e.getMaxLikeCallsPerRun(), false);
        if (result.commentLikesApplied() > 0 || result.replyLikesApplied() > 0 || result.viewsUpdated() > 0) {
            log.info("PlanEngagementDispatcher: posts={} views={} commentLikes={} replyLikes={}",
                    result.postsScanned(), result.viewsUpdated(), result.commentLikesApplied(), result.replyLikesApplied());
        }
    }

    /**
     * Single entry point for both scheduled reconciliation and manual backfill — the only
     * difference is {@code lookbackDays}. {@code dryRun=true} computes deficits without
     * dispatching anything (used to sanity-check the formulas before a live run).
     */
    public EngagementResult reconcile(int lookbackDays, int maxPosts, int maxLikeCalls, boolean dryRun) {
        OrchestratorProperties.Engagement cfg = properties.getThreadPlan().getEngagement();

        int viewsUpdated = 0;
        if (cfg.isViewsEnabled() && !dryRun) {
            // Views before likes, same run: like targets are a function of view count, and
            // ViewDispatcher deliberately excludes likes from its own formula to avoid a
            // circular amplification loop between the two.
            viewsUpdated = viewDispatcher.dispatchViews();
        }

        List<String> postIds = reader.planModePostIds(lookbackDays, maxPosts);
        List<Persona> activePersonas = personaRepository.findByActiveTrue();

        int likeCallsUsed = 0;
        int commentLikesApplied = 0;
        int replyLikesApplied = 0;
        List<PostDeficit> deficits = dryRun ? new ArrayList<>() : null;

        outer:
        for (String postId : postIds) {
            PostSnapshot snapshot = reader.snapshot(postId);
            if (snapshot == null) continue;

            for (CommentRow comment : snapshot.comments()) {
                if (likeCallsUsed >= maxLikeCalls) break outer;

                boolean isReply = comment.parentCommentId() != null;
                int target = isReply
                        ? EngagementTargetCalculator.replyLikeTarget(
                                snapshot.viewCount(), comment.id(), cfg.getReplyLikePerView(), cfg.getReplyLikeCap())
                        : EngagementTargetCalculator.commentLikeTarget(
                                snapshot.viewCount(), comment.childReplyCount(), comment.id(),
                                cfg.getCommentLikePerView(), cfg.getCommentLikePerReply(), cfg.getCommentLikeCap());
                int deficit = EngagementTargetCalculator.deficit(target, comment.likeCount());
                if (deficit <= 0) continue;

                if (dryRun) {
                    deficits.add(new PostDeficit(postId, comment.id(), isReply, deficit));
                    continue;
                }

                Set<String> alreadyLiked = reader.alreadyLikedCommentAuthorIds(comment.id());
                List<Persona> candidates = new ArrayList<>();
                for (Persona p : activePersonas) {
                    if (p.getId().equals(comment.authorId())) continue; // no self-likes
                    if (alreadyLiked.contains(p.getId())) continue;     // avoid likeComment's toggle-off-then-on
                    candidates.add(p);
                }
                Collections.shuffle(candidates);

                int toApply = Math.min(deficit, candidates.size());
                toApply = Math.min(toApply, maxLikeCalls - likeCallsUsed);

                PostDto stub = new PostDto();
                stub.setId(postId);
                for (int i = 0; i < toApply; i++) {
                    Persona persona = candidates.get(i);
                    try {
                        actionExecutor.execute(persona, PlannedAction.commentLike(stub, comment.id()));
                    } catch (Exception ex) {
                        log.debug("PlanEngagementDispatcher: like failed persona={} comment={}: {}",
                                persona.getId(), comment.id(), ex.getMessage());
                        continue;
                    }
                    likeCallsUsed++;
                    if (isReply) replyLikesApplied++; else commentLikesApplied++;
                }
            }
        }

        return new EngagementResult(postIds.size(), viewsUpdated, commentLikesApplied, replyLikesApplied, deficits);
    }

    public record PostDeficit(String postId, long commentId, boolean isReply, int deficit) { }

    public record EngagementResult(int postsScanned, int viewsUpdated, int commentLikesApplied,
                                    int replyLikesApplied, List<PostDeficit> deficits) { }
}
