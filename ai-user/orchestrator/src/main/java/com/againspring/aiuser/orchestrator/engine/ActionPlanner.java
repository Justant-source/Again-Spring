package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PostAnalysis;
import com.againspring.aiuser.orchestrator.repository.PersonaSeenPostRepository;
import com.againspring.aiuser.orchestrator.service.PostAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 페르소나에게 어떤 행동을 할지 결정.
 * 좋아요·투표는 PostAnalysis(글마다 1회 캐시) + 페르소나 필드로 로컬 산정 — LLM 토큰 0.
 * 분석은 캐시 조회만(getCached) — LLM 트리거는 BehaviorEngine이 틱당 budget 제한으로 수행.
 * 분석값 없으면 기존 affinity/bias 동작으로 graceful degrade.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionPlanner {

    private final PersonaSeenPostRepository seenPostRepo;
    private final PostAnalysisService analysisService;
    private final JdbcTemplate jdbcTemplate;
    private static final Random RNG = new Random();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // ── 행동 타입 확률 (voice_profile의 like_score/vote_score 우선) ──
    private static final double P_LIKE_DEFAULT = 0.45;
    private static final double P_VOTE_DEFAULT = 0.30;
    private static final double P_COMMENT = 0.20;
    private static final double P_REPLY_BASE = 0.15;
    private static final double P_POST = 0.05;

    // ── 좋아요 공명(resonance) 가중치 — 합 1.0 ──
    private static final double W_INTERESTS  = 0.35;
    private static final double W_EMOTIONS   = 0.35;
    private static final double W_HOTBUTTON  = 0.30;
    // 선별 게이트: gate = clamp(LIKE_GATE_SLOPE*(resonance - LIKE_GATE_PIVOT), 0.05, 0.95)
    // 공명 낮은 글은 대부분 건너뜀, 높은 글은 거의 누름.
    private static final double LIKE_GATE_SLOPE = 1.4;
    private static final double LIKE_GATE_PIVOT = 0.25;

    // ── 투표 방향성(현실적 합의) ──
    // score = (k1*(author_sympathy-0.5) + k2*political_strength*bias[cat] + k3*archetypeAlign)*(1-0.3*ambiguity)
    // authorProb = clamp(sigmoid(score), 0.12, 0.88) → 명백한 글 ~80% 쏠림 + 소수 반대표 항상 보장.
    private static final double VOTE_K_CONTENT   = 3.0;
    private static final double VOTE_K_PERSONA   = 1.2;
    private static final double VOTE_K_ARCHETYPE = 0.4;
    private static final double VOTE_AMBIGUITY_DAMP = 0.3;
    private static final double VOTE_PROB_FLOOR = 0.12;
    private static final double VOTE_PROB_CEIL  = 0.88;

    /**
     * Plan one action for the given persona.
     * @param postBudgetRemaining 이번 틱에 남은 POST 허용량. 0이면 POST 금지, 음수이면 쿼터 비활성(legacy 호환).
     */
    public Optional<PlannedAction> plan(Persona persona,
                                         List<PostDto> feedPosts,
                                         List<ReplyTarget> replyTargets,
                                         int postBudgetRemaining) {
        // Filter already-seen posts
        List<PostDto> unseen = feedPosts.stream()
            .filter(p -> p.getId() != null)
            .filter(p -> !seenPostRepo.existsByPersonaIdAndPostId(persona.getId(), p.getId()))
            .collect(Collectors.toList());

        // 자기 댓글에 자답 금지 — 본인이 작성한 댓글 타겟 제외
        List<ReplyTarget> eligibleReplies = replyTargets.stream()
            .filter(rt -> !persona.getId().equals(rt.commentAuthorId()))
            .collect(Collectors.toList());

        boolean canReply = !eligibleReplies.isEmpty();
        boolean canPost = "HEAVY".equals(persona.getTier());
        boolean hasFeed = !unseen.isEmpty();

        double rand = RNG.nextDouble();
        double cumul = 0;

        double pLike = voiceScore(persona, "like_score", P_LIKE_DEFAULT);
        double pVote = voiceScore(persona, "vote_score", P_VOTE_DEFAULT);

        // REPLY (priority when available)
        cumul += canReply ? P_REPLY_BASE : 0;
        if (rand < cumul && canReply) {
            ReplyTarget rt = eligibleReplies.get(RNG.nextInt(eligibleReplies.size()));
            return Optional.of(PlannedAction.reply(
                rt.postId(), rt.postTitle(), rt.commentId(), rt.commentExcerpt(), rt.threadContext(),
                rt.postBodyExcerpt(), rt.siblingComments()));
        }

        // VOTE (방향성·콘텐츠 인식) — LIKE 앞으로 이동: 투표 기근 해소
        cumul += hasFeed ? pVote : 0;
        if (rand < cumul && hasFeed) {
            PostDto post = pickByVoteScore(persona, unseen);
            if (post != null) {
                Long optionId = pickVoteOptionByContent(persona, post);
                if (optionId != null) {
                    return Optional.of(PlannedAction.vote(post, optionId));
                }
            }
            // 투표가능 글 없음 → fallthrough to LIKE (Optional.empty() 반환 제거로 낭비 없앰)
        }

        // LIKE / COMMENT_LIKE (같은 확률 밴드 — like_score 성향으로 분기)
        cumul += hasFeed ? pLike : 0;
        if (rand < cumul && hasFeed) {
            // like_score에 비례해 댓글 좋아요 비율 결정 (0.0~0.55)
            // like_score 높은 페르소나일수록 댓글 좋아요를 더 자주 선택
            double commentLikeRatio = voiceScore(persona, "like_score", P_LIKE_DEFAULT) * 0.55;
            if (RNG.nextDouble() < commentLikeRatio) {
                // 전체 피드(seen 포함) 중 관심도 기반 선택 — 기존 댓글 소급 적용 위해
                List<PostDto> pool = !feedPosts.isEmpty() ? feedPosts : unseen;
                PostDto target = pickByAffinity(persona, pool);
                if (target != null) return Optional.of(PlannedAction.commentLike(target));
            }
            PostDto chosen = pickByLikeScore(persona, unseen);
            if (chosen != null && passesLikeGate(persona, chosen)) {
                return Optional.of(PlannedAction.like(chosen));
            }
            // 공명하는 글 없음 → fallthrough to COMMENT (선별적, 낭비 없앰)
        }

        // COMMENT
        cumul += hasFeed ? P_COMMENT : 0;
        if (rand < cumul && hasFeed) {
            return Optional.of(PlannedAction.comment(pickByAffinity(persona, unseen)));
        }

        // POST (HEAVY only, 1인 1일 1글, 틱당 POST 예산 내)
        // postBudgetRemaining < 0 → 쿼터 비활성(legacy), >= 0 → 쿼터 시행
        boolean postAllowedByQuota = postBudgetRemaining < 0 || postBudgetRemaining > 0;
        if (canPost && postAllowedByQuota && !alreadyPostedToday(persona)) {
            // 쿼터 내에서는 P_POST 확률 완화: 예산이 있으면 적극 소비 (언더슈트 방지)
            double effectivePPost = postBudgetRemaining > 0 ? Math.max(P_POST, 0.15) : P_POST;
            if (RNG.nextDouble() < effectivePPost) {
                return Optional.of(PlannedAction.newPost());
            }
        }

        return Optional.empty();
    }

    /**
     * 오늘(KST) 이미 글을 1개 이상 작성했으면 true — 1인 1일 1글 규칙 enforcement.
     * posts 테이블 직접 조회(닉네임=author 기준 source of truth) — 재배정·삭제도 정확히 반영.
     */
    private boolean alreadyPostedToday(Persona persona) {
        try {
            Instant since = LocalDate.now(KST).atStartOfDay(KST).toInstant();
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE author_id = ? AND deleted_at IS NULL AND created_at >= ?",
                Long.class, persona.getId(), java.sql.Timestamp.from(since));
            return count != null && count >= 1;
        } catch (Exception e) {
            log.warn("alreadyPostedToday check failed for persona {}: {}", persona.getId(), e.getMessage());
            return false; // 조회 실패 시 가용성 우선 — 막지 않음
        }
    }

    // ══════════════════════ LIKE ══════════════════════

    /** 좋아요 후보 글을 공명 점수로 가중 추출. */
    private PostDto pickByLikeScore(Persona persona, List<PostDto> posts) {
        if (posts.isEmpty()) return null;
        double[] weights = posts.stream().mapToDouble(p -> likeWeight(persona, p)).toArray();
        return weightedPick(posts, weights);
    }

    /** 좋아요 가중치 = like_score × (0.3 + 0.7×resonance). */
    private double likeWeight(Persona persona, PostDto post) {
        double personaLike = voiceScore(persona, "like_score", P_LIKE_DEFAULT);
        double resonance = resonance(persona, post);
        return personaLike * (0.3 + 0.7 * resonance);
    }

    /** 선별 게이트 — 선택된 글의 공명도가 낮으면 좋아요를 건너뜀. */
    private boolean passesLikeGate(Persona persona, PostDto post) {
        double r = resonance(persona, post);
        double gate = clamp(LIKE_GATE_SLOPE * (r - LIKE_GATE_PIVOT), 0.05, 0.95);
        return RNG.nextDouble() < gate;
    }

    /**
     * 글-페르소나 공명도 [0,1] = 0.35·관심도 + 0.35·감정강도 + 0.30·hot_button 일치.
     * 분석 없으면 관심도 기반으로 degrade.
     */
    private double resonance(Persona persona, PostDto post) {
        double interestMatch = interestAffinity(persona, post);
        PostAnalysis a = (post.getId() != null) ? analysisService.getCached(post.getId()) : null;
        if (a == null) {
            // degrade: 관심도만 (기존 동작 근사)
            return clamp(interestMatch, 0.0, 1.0);
        }
        double severity = bd(a.getSeverity());
        double emotionalAppeal = severity >= 0.7 ? 1.0 : severity / 0.7;
        double hotButton = hotButtonResonance(persona, a);
        double res = W_INTERESTS * interestMatch
                   + W_EMOTIONS * emotionalAppeal
                   + W_HOTBUTTON * hotButton;
        return clamp(res, 0.0, 1.0);
    }

    /**
     * hot_button 공명 [0,1].
     * 016+ 생성형: voice_profile.hot_buttons.{triggers,soft_spots} ↔ 분석 topics/emotions 교집합.
     * 001–015 앵커: like_criteria 텍스트에 topic/emotion 키워드 등장 여부.
     * 둘 다 없으면 중립 baseline 0.3.
     */
    @SuppressWarnings("unchecked")
    private double hotButtonResonance(Persona persona, PostAnalysis a) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return 0.3;

        Object hbObj = vp.get("hot_buttons");
        if (hbObj instanceof Map) {
            Map<String, Object> hb = (Map<String, Object>) hbObj;
            double triggerMatch = listOverlap(asStrList(hb.get("triggers")), a.getTopics());
            double softMatch = listOverlap(asStrList(hb.get("soft_spots")), a.getEmotions());
            return clamp(0.5 * triggerMatch + 0.5 * softMatch, 0.0, 1.0);
        }

        Object crit = vp.get("like_criteria");
        if (crit instanceof String && !((String) crit).isBlank()) {
            String c = ((String) crit).toLowerCase();
            List<String> signals = new ArrayList<>();
            signals.addAll(a.getTopics());
            signals.addAll(a.getEmotions());
            boolean hit = signals.stream().anyMatch(s -> !s.isBlank() && c.contains(s.toLowerCase()));
            return hit ? 0.55 : 0.2;
        }
        return 0.3;
    }

    // ══════════════════════ VOTE ══════════════════════

    /** 투표 후보 글(작성자/상대방 옵션 ≥2)을 vote 관련도로 가중 추출. */
    private PostDto pickByVoteScore(Persona persona, List<PostDto> posts) {
        List<PostDto> votable = posts.stream()
            .filter(p -> p.getVoteOptions() != null && p.getVoteOptions().size() >= 2)
            .collect(Collectors.toList());
        if (votable.isEmpty()) return null;
        double[] weights = votable.stream().mapToDouble(p -> voteWeight(persona, p)).toArray();
        return weightedPick(votable, weights);
    }

    /** 투표 가중치 = vote_score × 관심도 × (0.5 + 0.5×severity). 격한 글일수록 투표 유발. */
    private double voteWeight(Persona persona, PostDto post) {
        double personaVote = voiceScore(persona, "vote_score", P_VOTE_DEFAULT);
        double categoryAffinity = interestAffinity(persona, post);
        PostAnalysis a = (post.getId() != null) ? analysisService.getCached(post.getId()) : null;
        double severityBoost = (a != null) ? 0.5 + 0.5 * bd(a.getSeverity()) : 0.75;
        return personaVote * categoryAffinity * severityBoost;
    }

    /** 작성자/상대방 옵션 결정 — 콘텐츠(author_sympathy) + 페르소나(정치성향·편향) 결합. */
    private Long pickVoteOptionByContent(Persona persona, PostDto post) {
        List<PostDto.VoteOptionDto> options = post.getVoteOptions();
        if (options == null || options.isEmpty()) return null;
        if (options.size() == 1) return options.get(0).getId();

        Long authorId = options.stream().filter(o -> "작성자".equals(o.getLabel()))
            .map(PostDto.VoteOptionDto::getId).findFirst().orElse(null);
        Long partnerId = options.stream().filter(o -> "상대방".equals(o.getLabel()))
            .map(PostDto.VoteOptionDto::getId).findFirst().orElse(null);
        if (authorId == null || partnerId == null) return options.get(0).getId();

        double authorProb = computeAuthorProb(persona, post);
        return RNG.nextDouble() < authorProb ? authorId : partnerId;
    }

    /** 작성자 옵션 투표 확률 [VOTE_PROB_FLOOR, VOTE_PROB_CEIL]. */
    private double computeAuthorProb(Persona persona, PostDto post) {
        double categoryBias = categoryBias(persona, post);
        PostAnalysis a = (post.getId() != null) ? analysisService.getCached(post.getId()) : null;

        if (a == null) {
            // degrade: 편향만 반영 (현행 동작과 동일 계열)
            return clamp(0.5 + categoryBias * 3.0, 0.15, 0.85);
        }

        double contentTerm = VOTE_K_CONTENT * (bd(a.getAuthorSympathy()) - 0.5);
        double politicalStrength = voiceScore(persona, "political_strength", 0.5);
        double personaTerm = VOTE_K_PERSONA * politicalStrength * categoryBias;

        double archetypeTerm = 0.0;
        if (a.getArchetypeFrame() != null && a.getArchetypeFrame().equalsIgnoreCase(persona.getArchetype())) {
            archetypeTerm = VOTE_K_ARCHETYPE * Math.signum(categoryBias);  // 페르소나 성향 강화
        }

        double ambiguityFactor = 1.0 - VOTE_AMBIGUITY_DAMP * bd(a.getAmbiguity());
        double score = (contentTerm + personaTerm + archetypeTerm) * ambiguityFactor;
        double authorProb = 1.0 / (1.0 + Math.exp(-score));  // sigmoid
        return clamp(authorProb, VOTE_PROB_FLOOR, VOTE_PROB_CEIL);
    }

    // ══════════════════════ Helpers ══════════════════════

    private double voiceScore(Persona persona, String key, double fallback) {
        try {
            if (persona.getVoiceProfile() == null) return fallback;
            Object v = persona.getVoiceProfile().get(key);
            if (v instanceof Number) return Math.max(0.05, Math.min(0.95, ((Number) v).doubleValue()));
        } catch (Exception ignored) {}
        return fallback;
    }

    private double interestAffinity(Persona persona, PostDto post) {
        if (post == null || post.getCategory() == null) return 0.1;
        Map<String, Double> interests = persona.getInterests();
        if (interests == null || interests.isEmpty()) return 0.1;
        return interests.getOrDefault(post.getCategory(), 0.1);
    }

    private double categoryBias(Persona persona, PostDto post) {
        Map<String, Double> biasProfile = persona.getBiasProfile();
        if (biasProfile == null || post.getCategory() == null) return 0.0;
        return biasProfile.getOrDefault(post.getCategory(), 0.0);
    }

    /** 관심도 가중 글 선택 (분석 불필요 — COMMENT 등 fallback 경로). */
    private PostDto pickByAffinity(Persona persona, List<PostDto> posts) {
        Map<String, Double> interests = persona.getInterests();
        if (interests == null || interests.isEmpty()) {
            return posts.get(RNG.nextInt(posts.size()));
        }
        double[] weights = posts.stream()
            .mapToDouble(p -> interests.getOrDefault(p.getCategory(), 0.1))
            .toArray();
        return weightedPick(posts, weights);
    }

    /** 가중치 비례 1개 추출. 합≤0이면 균등 랜덤. */
    private PostDto weightedPick(List<PostDto> posts, double[] weights) {
        if (posts.isEmpty()) return null;
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

    private double bd(java.math.BigDecimal v) {
        return v != null ? v.doubleValue() : 0.5;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @SuppressWarnings("unchecked")
    private List<String> asStrList(Object obj) {
        if (obj instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) obj) if (o != null) out.add(o.toString());
            return out;
        }
        return Collections.emptyList();
    }

    /** 부분일치 허용 교집합 비율 [0,1] — 한국어 키워드(연락 ↔ 연락 빈도) 대응. */
    private double listOverlap(List<String> a, List<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return 0.0;
        long m = a.stream().filter(x -> !x.isBlank() && b.stream()
            .anyMatch(y -> !y.isBlank() && (y.contains(x) || x.contains(y)))).count();
        return Math.min(1.0, (double) m / a.size());
    }
}
