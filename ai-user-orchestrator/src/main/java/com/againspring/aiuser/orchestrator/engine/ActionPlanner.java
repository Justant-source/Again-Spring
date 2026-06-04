package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaSeenPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 페르소나에게 어떤 행동을 할지 결정.
 * LLM 미사용 — 확률·affinity 기반.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionPlanner {

    private final PersonaSeenPostRepository seenPostRepo;
    private static final Random RNG = new Random();

    // 기본값 — voice_profile에 like_score/vote_score 없을 때 폴백
    private static final double P_LIKE_DEFAULT = 0.45;
    private static final double P_VOTE_DEFAULT = 0.30;
    private static final double P_COMMENT = 0.20;
    private static final double P_REPLY_BASE = 0.15;
    private static final double P_POST = 0.05;

    /**
     * Plan one action for the given persona.
     *
     * @param persona       acting persona
     * @param feedPosts     recent posts from community feed
     * @param replyTargets  reply opportunities from InteractionScanner
     * @return planned action, or empty if no suitable target
     */
    public Optional<PlannedAction> plan(Persona persona,
                                         List<PostDto> feedPosts,
                                         List<ReplyTarget> replyTargets) {
        // Filter already-seen posts
        List<PostDto> unseen = feedPosts.stream()
            .filter(p -> p.getId() != null)
            .filter(p -> !seenPostRepo.existsByPersonaIdAndPostId(persona.getId(), p.getId()))
            .collect(Collectors.toList());

        boolean canReply = !replyTargets.isEmpty();
        boolean canPost = "HEAVY".equals(persona.getTier());
        boolean hasFeed = !unseen.isEmpty();

        double rand = RNG.nextDouble();
        double cumul = 0;

        // 페르소나별 like/vote 확률 (voice_profile의 like_score/vote_score 우선)
        double pLike = voiceScore(persona, "like_score", P_LIKE_DEFAULT);
        double pVote = voiceScore(persona, "vote_score", P_VOTE_DEFAULT);

        // REPLY check (priority when available)
        cumul += canReply ? P_REPLY_BASE : 0;
        if (rand < cumul && canReply) {
            ReplyTarget rt = replyTargets.get(RNG.nextInt(replyTargets.size()));
            return Optional.of(PlannedAction.reply(
                rt.postId(), rt.postTitle(), rt.commentId(), rt.commentExcerpt(), rt.threadContext(),
                rt.postBodyExcerpt(), rt.siblingComments()));
        }

        // LIKE
        cumul += hasFeed ? pLike : 0;
        if (rand < cumul && hasFeed) {
            return Optional.of(PlannedAction.like(pickByAffinity(persona, unseen)));
        }

        // VOTE
        cumul += hasFeed ? pVote : 0;
        if (rand < cumul && hasFeed) {
            PostDto post = pickByAffinity(persona, unseen);
            Long optionId = pickVoteOption(persona, post);
            if (optionId != null) {
                return Optional.of(PlannedAction.vote(post, optionId));
            }
        }

        // COMMENT
        cumul += hasFeed ? P_COMMENT : 0;
        if (rand < cumul && hasFeed) {
            return Optional.of(PlannedAction.comment(pickByAffinity(persona, unseen)));
        }

        // POST (HEAVY only)
        if (canPost && RNG.nextDouble() < P_POST) {
            return Optional.of(PlannedAction.newPost());
        }

        return Optional.empty();
    }

    /** voice_profile에서 숫자 점수 읽기 (없으면 fallback) */
    private double voiceScore(Persona persona, String key, double fallback) {
        try {
            if (persona.getVoiceProfile() == null) return fallback;
            Object v = persona.getVoiceProfile().get(key);
            if (v instanceof Number) return Math.max(0.05, Math.min(0.95, ((Number) v).doubleValue()));
        } catch (Exception ignored) {}
        return fallback;
    }

    /**
     * Pick a post weighted by persona's category affinity.
     * Falls back to random if no interests or no match.
     */
    private PostDto pickByAffinity(Persona persona, List<PostDto> posts) {
        Map<String, Double> interests = persona.getInterests();
        if (interests == null || interests.isEmpty()) {
            return posts.get(RNG.nextInt(posts.size()));
        }
        double[] weights = posts.stream()
            .mapToDouble(p -> interests.getOrDefault(p.getCategory(), 0.1))
            .toArray();
        double total = Arrays.stream(weights).sum();
        if (total <= 0) return posts.get(RNG.nextInt(posts.size()));
        double r = RNG.nextDouble() * total;
        double cum = 0;
        for (int i = 0; i < posts.size(); i++) {
            cum += weights[i];
            if (r <= cum) return posts.get(i);
        }
        return posts.get(posts.size() - 1);
    }

    /**
     * Pick vote option based on persona bias_profile.
     * bias > 0 → lean toward "작성자" (AUTHOR), bias < 0 → lean toward "상대방" (PARTNER).
     * Returns null if post has no vote options.
     */
    private Long pickVoteOption(Persona persona, PostDto post) {
        List<PostDto.VoteOptionDto> options = post.getVoteOptions();
        if (options == null || options.isEmpty()) return null;
        if (options.size() == 1) return options.get(0).getId();

        double bias = 0.0;
        Map<String, Double> biasProfile = persona.getBiasProfile();
        if (biasProfile != null && post.getCategory() != null) {
            bias = biasProfile.getOrDefault(post.getCategory(), 0.0);
        }
        // bias in [-1, 1]. 0.5 + bias/2 = probability of choosing first option (작성자)
        double probFirst = Math.max(0.05, Math.min(0.95, 0.5 + bias / 2.0));
        return RNG.nextDouble() < probFirst ? options.get(0).getId() : options.get(1).getId();
    }
}
