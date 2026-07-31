package com.againspring.aiuser.orchestrator.service.engagement;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
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
 * Converges comment/reply likes, post-level likes, votes, and post view counts toward the
 * formula targets in {@code docs/ai-user/thread-planning.md} §LLM 없는 engagement, entirely
 * independently of {@code BehaviorEngine}'s (now-removed) tick loop.
 *
 * <p>Deliberately re-evaluated on a timer rather than triggered at publish time: a comment's
 * correct like target depends on the post's view count and its own reply count, neither of
 * which are known the instant it publishes. Because {@link #reconcile} computes
 * {@code deficit = target - current} and only ever applies the shortfall, backfilling old
 * content and steady-state operation are the same call with a wider {@code lookbackDays} —
 * there is no separate backfill code path to drift out of sync with this one.
 *
 * <p><b>Scope: views, comment/reply likes, post likes, and votes (2026-07-31~).</b> This class
 * used to exclude post likes/votes in favor of {@code VoteLikeBatchService}, a separate
 * global-daily-quota batch gated on the {@code provider_vote_like} DB flag. That flag defaulted
 * to (and stayed) {@code 'OFF'} in prod because nothing ever turned it on — the nightly batch
 * script only toggles the three LLM providers — so votes/post-likes silently went to zero once
 * the LEGACY tick loop (which had run them unconditionally) was removed. {@code
 * VoteLikeBatchService} and its scheduler have been deleted; this class now dispatches both,
 * using the same per-post deficit convergence as comment/reply likes so neither depends on a
 * DB flag nobody flips.
 *
 * <p>Does not touch {@code ai_user_runtime.daily_global_cap} — that counter is written
 * exclusively by the (now-removed) LEGACY tick loop, and sharing it would mean engagement
 * silently stops the moment that cap is reached by unrelated activity, which is exactly the
 * failure mode this class exists to fix. Volume is bounded per-run instead
 * ({@code maxPostsPerRun}, {@code maxLikeCallsPerRun}, {@code maxVoteCallsPerRun}).
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
    private final BotTokenCache botTokenCache;

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
        if (result.commentLikesApplied() > 0 || result.replyLikesApplied() > 0 || result.viewsUpdated() > 0
                || result.votesApplied() > 0 || result.postLikesApplied() > 0) {
            log.info("PlanEngagementDispatcher: posts={} views={} commentLikes={} replyLikes={} votes={} postLikes={}",
                    result.postsScanned(), result.viewsUpdated(), result.commentLikesApplied(), result.replyLikesApplied(),
                    result.votesApplied(), result.postLikesApplied());
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

        List<String> postIds = new ArrayList<>(reader.planModePostIds(lookbackDays, maxPosts));
        // 조회수 순(=최신순) 정렬로 들어오는 목록을 섞어서, maxLikeCalls 소진으로 루프가
        // 중간에 끊길 때 매번 같은(오래된) 글부터 영원히 처리되는 편향을 없앤다.
        Collections.shuffle(postIds);
        // 매 댓글마다 활성 페르소나 전체(~150명)를 다시 후보로 쓰면 이번 실행에서 처음 로그인하는
        // 페르소나 수가 과도해져 백엔드 로그인 레이트리밋(분당 5회/IP)에 걸린다. 실행당 한 번만
        // warm(이미 토큰 캐시됨) 우선 + cold(신규 로그인) 소량 예산으로 풀을 구성해 재사용한다.
        List<Persona> personaPool = dryRun
                ? List.of()
                : buildPersonaPool(personaRepository.findByActiveTrue(), cfg);

        int likeCallsUsed = 0;
        int commentLikesApplied = 0;
        int replyLikesApplied = 0;
        int voteCallsUsed = 0;
        int votesApplied = 0;
        int postLikesApplied = 0;
        List<PostDeficit> deficits = dryRun ? new ArrayList<>() : null;

        outer:
        for (String postId : postIds) {
            PostSnapshot snapshot = reader.snapshot(postId);
            if (snapshot == null) continue;

            // --- VOTE --- (own budget: maxVoteCallsPerRun, separate from maxLikeCalls so a
            // comment-like backfill can never starve votes out entirely, or vice versa)
            if (cfg.isVotesEnabled()) {
                List<EngagementSnapshotReader.VoteOptionRow> options = reader.voteOptions(postId);
                Long optionAId = options.stream().filter(o -> o.orderIdx() == 0)
                        .findFirst().map(EngagementSnapshotReader.VoteOptionRow::id).orElse(null);
                Long optionBId = options.stream().filter(o -> o.orderIdx() == 1)
                        .findFirst().map(EngagementSnapshotReader.VoteOptionRow::id).orElse(null);
                if (optionAId != null && optionBId != null) {
                    Map<Long, Integer> voteCounts = reader.voteCountsByOption(postId);
                    int currentA = voteCounts.getOrDefault(optionAId, 0);
                    int currentB = voteCounts.getOrDefault(optionBId, 0);
                    int voteTarget = EngagementTargetCalculator.voteTarget(
                            snapshot.viewCount(), postId, cfg.getVotePerView(), cfg.getVoteCap());
                    int voteDeficit = EngagementTargetCalculator.deficit(voteTarget, currentA + currentB);
                    if (voteDeficit > 0) {
                        if (dryRun) {
                            deficits.add(new PostDeficit(postId, null, "VOTE", voteDeficit));
                        } else if (voteCallsUsed < cfg.getMaxVoteCallsPerRun()) {
                            double targetAShare = EngagementTargetCalculator.voteAShare(
                                    postId, cfg.getVoteAShareMin(), cfg.getVoteAShareMax());
                            // votes.UNIQUE(post_id, voter_user_id) — backend returns 409 on a second
                            // vote from the same user, so this pre-filter is mandatory, not just an
                            // optimization (see VoteService.java:61-66).
                            Set<String> alreadyVoted = reader.alreadyVotedUserIds(postId);
                            List<Persona> voteCandidates = new ArrayList<>();
                            for (Persona p : personaPool) {
                                if (alreadyVoted.contains(p.getId())) continue;
                                voteCandidates.add(p);
                            }
                            Collections.shuffle(voteCandidates);

                            int voteToApply = Math.min(voteDeficit, voteCandidates.size());
                            voteToApply = Math.min(voteToApply, cfg.getMaxVoteCallsPerRun() - voteCallsUsed);
                            voteToApply = Math.min(voteToApply, cfg.getMaxVotesPerPostPerRun());

                            PostDto voteStub = new PostDto();
                            voteStub.setId(postId);
                            int runningA = currentA;
                            int runningB = currentB;
                            for (int i = 0; i < voteToApply; i++) {
                                Persona persona = voteCandidates.get(i);
                                int optionIdx = EngagementTargetCalculator.chooseVoteOptionIndex(runningA, runningB, targetAShare);
                                Long optionId = optionIdx == 0 ? optionAId : optionBId;
                                try {
                                    actionExecutor.execute(persona, PlannedAction.vote(voteStub, optionId));
                                } catch (Exception ex) {
                                    log.debug("PlanEngagementDispatcher: vote failed persona={} post={}: {}",
                                            persona.getId(), postId, ex.getMessage());
                                    continue;
                                }
                                if (!botTokenCache.hasValidToken(persona.getId())) {
                                    personaPool.remove(persona);
                                    continue;
                                }
                                voteCallsUsed++;
                                votesApplied++;
                                if (optionIdx == 0) runningA++; else runningB++;
                            }
                        }
                    }
                }
            }

            // --- POST LIKE --- (shares maxLikeCalls with comment/reply likes below — same
            // toggle-like nature and risk profile as those, no reason for a separate budget)
            if (cfg.isPostLikesEnabled()) {
                int postLikeTarget = EngagementTargetCalculator.postLikeTarget(
                        snapshot.viewCount(), snapshot.comments().size(), postId,
                        cfg.getPostLikePerView(), cfg.getPostLikePerComment());
                int postLikeDeficit = EngagementTargetCalculator.deficit(
                        postLikeTarget, (int) reader.currentPostLikeCount(postId));
                if (postLikeDeficit > 0) {
                    if (dryRun) {
                        deficits.add(new PostDeficit(postId, null, "POST_LIKE", postLikeDeficit));
                    } else if (likeCallsUsed < maxLikeCalls) {
                        // likePost toggles — a persona that already liked this post must be
                        // excluded, or re-dispatching to them would flip their like back off.
                        Set<String> alreadyLikedPost = reader.alreadyLikedPostAuthorIds(postId);
                        List<Persona> postLikeCandidates = new ArrayList<>();
                        for (Persona p : personaPool) {
                            if (p.getId().equals(snapshot.authorId())) continue; // no self-likes
                            if (alreadyLikedPost.contains(p.getId())) continue;
                            postLikeCandidates.add(p);
                        }
                        Collections.shuffle(postLikeCandidates);

                        int postLikeToApply = Math.min(postLikeDeficit, postLikeCandidates.size());
                        postLikeToApply = Math.min(postLikeToApply, maxLikeCalls - likeCallsUsed);

                        PostDto postLikeStub = new PostDto();
                        postLikeStub.setId(postId);
                        for (int i = 0; i < postLikeToApply; i++) {
                            Persona persona = postLikeCandidates.get(i);
                            try {
                                actionExecutor.execute(persona, PlannedAction.like(postLikeStub));
                            } catch (Exception ex) {
                                log.debug("PlanEngagementDispatcher: post-like failed persona={} post={}: {}",
                                        persona.getId(), postId, ex.getMessage());
                                continue;
                            }
                            if (!botTokenCache.hasValidToken(persona.getId())) {
                                personaPool.remove(persona);
                                continue;
                            }
                            likeCallsUsed++;
                            postLikesApplied++;
                        }
                    }
                }
            }

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
                    deficits.add(new PostDeficit(postId, comment.id(), isReply ? "REPLY" : "COMMENT", deficit));
                    continue;
                }

                Set<String> alreadyLiked = reader.alreadyLikedCommentAuthorIds(comment.id());
                List<Persona> candidates = new ArrayList<>();
                for (Persona p : personaPool) {
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
                    // ActionExecutor swallows a JWT-fetch failure (returns void, no exception) —
                    // detect it here via the cache instead. Without this, a single rate-limited
                    // cold persona keeps getting re-selected as a candidate for every remaining
                    // comment in this run and re-attempts login each time, turning one 429 into
                    // dozens. Dropping it from the pool bounds it to one failed attempt per run.
                    if (!botTokenCache.hasValidToken(persona.getId())) {
                        personaPool.remove(persona);
                        continue;
                    }
                    likeCallsUsed++;
                    if (isReply) replyLikesApplied++; else commentLikesApplied++;
                }
            }
        }

        return new EngagementResult(postIds.size(), viewsUpdated, commentLikesApplied, replyLikesApplied,
                votesApplied, postLikesApplied, deficits);
    }

    /**
     * Builds the 댓글 좋아요 후보 풀 once per {@link #reconcile} call instead of re-shuffling the
     * full active-persona list per comment. Warm personas (already have a cached, non-expired
     * bot JWT — see {@link BotTokenCache#hasValidToken}) fill the pool first since dispatching a
     * like via them costs zero logins; only the remaining slots are filled with cold personas,
     * capped by {@code coldLoginBudget}, so a single 5-minute run never asks the shared
     * per-IP login rate limiter (default 5/min in prod, {@code RateLimitFilter}) for more fresh
     * logins than it can grant alongside the other publish schedulers sharing that bucket.
     */
    private List<Persona> buildPersonaPool(List<Persona> activePersonas, OrchestratorProperties.Engagement cfg) {
        List<Persona> shuffled = new ArrayList<>(activePersonas);
        Collections.shuffle(shuffled);

        List<Persona> warm = new ArrayList<>();
        List<Persona> cold = new ArrayList<>();
        for (Persona p : shuffled) {
            (botTokenCache.hasValidToken(p.getId()) ? warm : cold).add(p);
        }

        List<Persona> pool = new ArrayList<>();
        for (Persona p : warm) {
            if (pool.size() >= cfg.getPersonaPoolSize()) break;
            pool.add(p);
        }
        int coldAdded = 0;
        for (Persona p : cold) {
            if (pool.size() >= cfg.getPersonaPoolSize() || coldAdded >= cfg.getColdLoginBudget()) break;
            pool.add(p);
            coldAdded++;
        }
        Collections.shuffle(pool);
        return pool;
    }

    /**
     * @param commentId null for VOTE/POST_LIKE (post-scoped, not comment-scoped)
     * @param kind one of {@code COMMENT}, {@code REPLY}, {@code VOTE}, {@code POST_LIKE}
     */
    public record PostDeficit(String postId, Long commentId, String kind, int deficit) { }

    public record EngagementResult(int postsScanned, int viewsUpdated, int commentLikesApplied,
                                    int replyLikesApplied, int votesApplied, int postLikesApplied,
                                    List<PostDeficit> deficits) { }
}
