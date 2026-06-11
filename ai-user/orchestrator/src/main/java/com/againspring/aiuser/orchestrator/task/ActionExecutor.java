package com.againspring.aiuser.orchestrator.task;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.*;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiGlobalRule;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaActionLog;
import com.againspring.aiuser.orchestrator.domain.PersonaSeenPost;
import com.againspring.aiuser.orchestrator.engine.ArchetypeCatalog;
import com.againspring.aiuser.orchestrator.engine.PlannedAction;
import com.againspring.aiuser.orchestrator.repository.AiGlobalRuleRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaSeenPostRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 계획된 행동 실행기.
 * (필요 시 LLM 본문 생성) → 안전 검사 → REST 제출 → 로그 기록.
 *
 * Phase 1·2·3·4: 페르소나 voice 예시 주입, archetype hot-button, 데모그래픽,
 * 기존 댓글 인지, 대댓글 실제 스레드 맥락, stance 다양화.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionExecutor {

    private final BotTokenCache tokenCache;
    private final BackendBotClient backendBot;
    private final LlmAiUserClient llmClient;
    private final ContentSafetyGuard safetyGuard;
    private final PersonaSeenPostRepository seenPostRepo;
    private final PersonaActionLogRepository actionLogRepo;
    private final OrchestratorProperties props;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ArchetypeCatalog archetypeCatalog;
    private final AiLearningClient aiLearningClient;
    private final AiGlobalRuleRepository aiGlobalRuleRepository;
    private final AiUserGenerationConfigRepository generationConfigRepository;

    @Value("${ai-user.history-dir:/app/persona-history}")
    private String historyDir;

    /** 반복 가드 임계 — 생성문 vs 최근 출력의 문자 2-gram Jaccard 최대값이 이 값을 넘으면 1회 재생성. */
    @Value("${ai-user.repetition-threshold:0.45}")
    private double repetitionThreshold;

    private final ConcurrentHashMap<String, String> emailCache = new ConcurrentHashMap<>();
    private static final Random RNG = new Random();

    /** 전역 금지 규칙 캐시 (5분 TTL). 매 생성마다 DB 조회 방지. */
    private final AtomicReference<List<AiGlobalRule>> globalRulesCache = new AtomicReference<>(null);
    private volatile long globalRulesCachedAt = 0L;

    /** 생성 설정 캐시 (5분 TTL). backend 라우팅용. */
    private final AtomicReference<AiUserGenerationConfig> genConfigCache = new AtomicReference<>(null);
    private volatile long genConfigCachedAt = 0L;
    private static final long GEN_CONFIG_TTL_MS = 5 * 60 * 1000L;

    private AiUserGenerationConfig getGenConfig() {
        long now = System.currentTimeMillis();
        if (genConfigCache.get() == null || now - genConfigCachedAt > GEN_CONFIG_TTL_MS) {
            generationConfigRepository.findById(1).ifPresent(c -> {
                genConfigCache.set(c);
                genConfigCachedAt = now;
            });
        }
        return genConfigCache.get();
    }

    private String backendFor(String actionType) {
        AiUserGenerationConfig cfg = getGenConfig();
        if (cfg == null) return "CLI";
        return cfg.effectiveBackend(actionType);
    }
    private static final long GLOBAL_RULES_TTL_MS = 5 * 60 * 1000L; // 5분

    public void execute(Persona persona, PlannedAction action) {
        String corrId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String email = botEmail(persona);
        java.util.Optional<String> jwtOpt = tokenCache.getToken(persona.getId(), email, props.getBotPassword());
        if (jwtOpt.isEmpty()) {
            log.warn("Cannot execute action for persona {}: no JWT", persona.getId());
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "no_jwt"));
            return;
        }
        String jwt = jwtOpt.get();

        switch (action.type()) {
            case LIKE -> executeLike(persona, action, jwt, corrId);
            case VOTE -> executeVote(persona, action, jwt, corrId);
            case COMMENT -> executeComment(persona, action, jwt, corrId);
            case REPLY -> executeReply(persona, action, jwt, corrId);
            case POST -> executePost(persona, action, jwt, corrId);
            case VIEW -> executeView(persona, action, jwt, corrId);
            case COMMENT_LIKE -> executeCommentLike(persona, action, jwt, corrId);
            default -> log.warn("Unhandled action type: {}", action.type());
        }
    }

    private void executeCommentLike(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.targetPost().getId() == null) return;
        String postId = action.targetPost().getId();
        CommentContext ctx = fetchReactableComments(postId);
        if (ctx.items().isEmpty()) {
            log.debug("COMMENT_LIKE skip: no comments on post {}", postId);
            return;
        }
        double likeThreshold = voiceScoreLocal(persona, "like_score", 0.45);
        int liked = 0;
        for (ReactableItem item : ctx.items()) {
            if (liked >= 3) break;
            if (item.authorId() != null && persona.getId().equals(item.authorId())) continue;
            if (Math.random() < likeThreshold) {
                boolean ok = backendBot.likeComment(jwt, postId, item.commentId());
                logPiggyback(persona, corrId, "COMMENT_LIKE", postId, item.commentId(), ok);
                if (ok) liked++;
            }
        }
        if (liked == 0) {
            log.debug("COMMENT_LIKE: no comments passed gate for persona {} on post {}", persona.getId(), postId);
        }
    }

    private void executeLike(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.targetPost().getId() == null) return;
        String postId = action.targetPost().getId();
        boolean ok = backendBot.likePost(jwt, postId);
        markSeen(persona, postId, true);
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "usedLlm", false));
    }

    private void executeView(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.targetPost().getId() == null) return;
        String postId = action.targetPost().getId();
        // deviceId = "ai-bot-{personaId}" — 페르소나당 포스트당 1회만 카운트
        String deviceId = "ai-bot-" + persona.getId();
        boolean ok = backendBot.viewPost(postId, deviceId);
        // VIEW는 markSeen 하지 않음 — 이후 댓글/투표 행동 차단 방지
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "usedLlm", false));
    }

    private void executeVote(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.voteOptionId() == null) return;
        String postId = action.targetPost().getId();
        boolean ok = backendBot.vote(jwt, postId, action.voteOptionId());
        markSeen(persona, postId, true);
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "optionId", action.voteOptionId(), "usedLlm", false));
    }

    private void executeComment(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null) return;
        String postId = action.targetPost().getId();
        String postTitle = action.targetPost().getUserTitle();
        String postExcerpt = truncate(action.targetPost().getBodyPublished(), 300);
        String stance = pickStanceWeighted(persona, action.targetPost());

        // Phase 4a: 댓글 조회 + 피기백 반응 대상 목록
        CommentContext ctx = fetchReactableComments(postId);

        // Phase 2d: archetype stance별 few-shot
        String archetypeCommentSamples = buildArchetypeCommentSamples(persona, action.targetPost());

        // Phase 3: demographic
        String demographic = demographicStr(persona);

        // 문체 앵커: voice 소스 크롤 코퍼스 랜덤 샘플 (문체 현실화 S2)
        // 주제-RAG(findSimilar) 대체 — 댓글은 주제 유사성보다 실제 댓글의 캐던스가 중요
        String styleExamples = styleExamplesFor(persona, "COMMENT", 3, 300);

        // 반복 방지: 최근 댓글 히스토리 주입 (문체 현실화 S1)
        java.util.List<String> recentBodies = loadRecentBodies(persona, "comments", 5);

        GenDto.CommentRequest genReq = GenDto.CommentRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceBlockForComment(persona, stance))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .postTitle(postTitle)
            .postBodyExcerpt(postExcerpt)
            .stance(stance)
            .category(action.targetPost().getCategory())
            .formality(voiceFormality(persona))
            .demographic(demographic)
            .archetypeCommentSamples(archetypeCommentSamples)
            .existingComments(null)  // reactableComments가 대체 (번호 형식으로 겸용)
            .reactableComments(ctx.promptList())
            .dispositionNote(dispositionNote(persona))
            .styleExamples(styleExamples)
            .correctionCautions(cautionsBlock(persona))
            .globalForbidRules(globalRulesBlock("COMMENT"))
            .correlationId(corrId)
            .backend(backendFor("COMMENT"))
            .recentOutputs(formatRecentOutputs(recentBodies, 150))
            .modeHint(commentModeHint(pickCommentMode(persona, stance)))
            .build();
        java.util.Optional<LlmAiUserClient.GenResult> resultOpt = llmClient.generateCommentR(genReq);

        if (resultOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        LlmAiUserClient.GenResult res = resultOpt.get();
        String text = res.text();

        // 반복 가드: 최근 출력과 거의 같으면 1회만 재생성, 그래도 같으면 그대로 게시 (활동 누락 방지)
        boolean repetitive = maxBigramJaccard(text, recentBodies) > repetitionThreshold;
        if (repetitive) {
            log.info("Repetitive comment for persona {} corr={} — regenerating once", persona.getId(), corrId);
            genReq.setRecentOutputs(repetitionRetryFeedback(genReq.getRecentOutputs(), text, "댓글"));
            java.util.Optional<LlmAiUserClient.GenResult> retryOpt = llmClient.generateCommentR(genReq);
            if (retryOpt.isPresent() && retryOpt.get().text() != null && !retryOpt.get().text().isBlank()) {
                res = retryOpt.get();
                text = res.text();
                repetitive = maxBigramJaccard(text, recentBodies) > repetitionThreshold;
            }
        }
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(text, ContentSafetyGuard.ContentType.COMMENT);
        if (!guard.passed()) {
            log.warn("Comment blocked for persona {} post {}: {}", persona.getId(), postId, guard.reason());
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        boolean ok = backendBot.addComment(jwt, postId, text, null);
        markSeen(persona, postId, true);
        if (ok) {
            writeHistory(persona, "comments", text, postId, null);
            // AI Learning: 합격한 댓글 예시 뱅크에 저장
            aiLearningClient.saveAsync(text, "COMMENT",
                action.targetPost() != null ? action.targetPost().getCategory() : "OTHER", "SELF_GENERATED");
            // 피기백 반응 디스패치 (추가 LLM 호출 0건)
            dispatchReactions(persona, jwt, action.targetPost(), res.reactionsJson(), ctx.items(), corrId);
        }
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "len", text.length(), "usedLlm", true, "repetitive", repetitive));
    }

    private void executeReply(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.parentCommentId() == null) return;
        String postId = action.targetPost().getId();

        // Phase 4b: 대댓글 stance 다양화 (CURIOUS 하드코딩 제거)
        String stance = pickReplyStance(persona);

        // Phase 4b: 원글 발췌 + 형제 댓글 (PlannedAction에서)
        String postBodyExcerpt = action.targetPost().getBodyPublished();
        String siblingComments = action.siblingComments();

        // 반복 방지: 최근 댓글(대댓글 포함) 히스토리 주입 (문체 현실화 S1)
        java.util.List<String> recentBodies = loadRecentBodies(persona, "comments", 5);

        GenDto.ReplyRequest genReq = GenDto.ReplyRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceBlockForReply(persona))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .parentCommentExcerpt(action.parentCommentExcerpt())
            .threadContext(action.threadContext())
            .stance(stance)
            .formality(voiceFormality(persona))
            .demographic(demographicStr(persona))
            .postBodyExcerpt(postBodyExcerpt)
            .siblingComments(siblingComments)
            .correctionCautions(cautionsBlock(persona))
            .globalForbidRules(globalRulesBlock("COMMENT"))
            .correlationId(corrId)
            .backend(backendFor("REPLY"))
            .dispositionNote(dispositionNote(persona))
            .styleExamples(styleExamplesFor(persona, "COMMENT", 2, 80))
            .recentOutputs(formatRecentOutputs(recentBodies, 150))
            .modeHint(replyLengthHint())
            .build();
        java.util.Optional<LlmAiUserClient.GenResult> resultOpt = llmClient.generateReplyR(genReq);

        if (resultOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        LlmAiUserClient.GenResult res = resultOpt.get();
        String text = res.text();

        // 반복 가드 (초단문은 charBigrams가 빈 셋을 반환해 자동 제외)
        if (maxBigramJaccard(text, recentBodies) > repetitionThreshold) {
            log.info("Repetitive reply for persona {} corr={} — regenerating once", persona.getId(), corrId);
            genReq.setRecentOutputs(repetitionRetryFeedback(genReq.getRecentOutputs(), text, "대댓글"));
            java.util.Optional<LlmAiUserClient.GenResult> retryOpt = llmClient.generateReplyR(genReq);
            if (retryOpt.isPresent() && retryOpt.get().text() != null && !retryOpt.get().text().isBlank()) {
                res = retryOpt.get();
                text = res.text();
            }
        }
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(text, ContentSafetyGuard.ContentType.COMMENT);
        if (!guard.passed()) {
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        boolean ok = backendBot.addComment(jwt, postId, text, action.parentCommentId());
        if (ok) {
            writeHistory(persona, "comments", text, postId, null);
            // 피기백 반응 디스패치 — 부모 댓글(항목 1)만 대상
            // ActionPlanner가 자작 댓글 reply 타겟을 사전 제외하므로 authorId 검사 불필요
            java.util.List<ReactableItem> parentItems = java.util.List.of(
                new ReactableItem(action.parentCommentId(), null));
            dispatchReactions(persona, jwt, action.targetPost(), res.reactionsJson(), parentItems, corrId);
        }
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("commentId", action.parentCommentId(), "len", text.length(), "usedLlm", true));
    }

    private void executePost(Persona persona, PlannedAction action, String jwt, String corrId) {
        String category = topCategory(persona);

        // Phase 2c: archetype 기반 topic seed (hot_button_phrases, emotional_beats)
        String topicSeed = buildTopicSeed(persona);

        // 글 길이 다양화 — 페르소나 tier 기반 가중 랜덤
        String lengthTier = pickLengthTier(persona);

        // AI Learning: 동적 예시 검색 및 주입 (RAG) (register 파라미터 전달)
        String dynamicExamples = "";
        String register = resolveRegister(persona);
        java.util.List<AiLearningClient.ExampleItem> examples = aiLearningClient.findSimilar(
            topicSeed,
            "POST",
            category,
            3,
            register
        );
        if (!examples.isEmpty()) {
            dynamicExamples = examples.stream()
                .map(e -> e.getContent())
                .collect(java.util.stream.Collectors.joining("\n---\n"));
            log.debug("RAG: {} posts found for {}", examples.size(), corrId);
        }
        if (dynamicExamples.isBlank()) {
            // 주제-RAG 미스 시 voice 소스 문체 샘플로 보충 (문체 현실화 S2)
            String styleFallback = styleExamplesFor(persona, "POST", 2, 600);
            if (styleFallback != null) dynamicExamples = styleFallback;
        }

        // 반복 방지: 최근 글 히스토리 주입 — 같은 소재·표현 재탕 차단 (문체 현실화 S1)
        java.util.List<String> recentBodies = loadRecentBodies(persona, "posts", 3);

        GenDto.PostRequest genReq = GenDto.PostRequest.builder()
            .personaId(persona.getId())
            .voiceProfile(voiceBlockForPost(persona))
            .slangLevel(persona.getSlangLevel().doubleValue())
            .archetype(persona.getArchetype())
            .tier(persona.getTier())
            .category(category)
            .topicSeed(topicSeed)
            .formality(voiceFormality(persona))
            .demographic(demographicStr(persona))
            .dynamicExamples(dynamicExamples)
            .lengthTier(lengthTier)
            .correctionCautions(cautionsBlock(persona))
            .globalForbidRules(globalRulesBlock("POST"))
            .correlationId(corrId)
            .backend(backendFor("POST"))
            .recentOutputs(formatRecentOutputs(recentBodies, 200))
            .build();
        java.util.Optional<String> bodyOpt = llmClient.generatePost(genReq);

        if (bodyOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        String rawBody = bodyOpt.get();

        // 반복 가드: 최근 글과 거의 같으면 1회만 재생성
        if (maxBigramJaccard(rawBody, recentBodies) > repetitionThreshold) {
            log.info("Repetitive post for persona {} corr={} — regenerating once", persona.getId(), corrId);
            genReq.setRecentOutputs(repetitionRetryFeedback(genReq.getRecentOutputs(), rawBody, "글"));
            java.util.Optional<String> retryOpt = llmClient.generatePost(genReq);
            if (retryOpt.isPresent() && !retryOpt.get().isBlank()) {
                rawBody = retryOpt.get();
            }
        }
        // 부수버그: LLM 메타텍스트 제거 (예: "[원문 수정본]", "[수정본]" 등)
        final String body = cleanLlmMetaText(rawBody);
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(body, ContentSafetyGuard.ContentType.POST);
        if (!guard.passed()) {
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        String title = extractTitle(body);
        java.util.Optional<PostDto> postOpt = backendBot.createPost(jwt, CreatePostDto.builder()
            .userTitle(title)
            .bodyRaw(body)
            .category(category)
            .visibility("PUBLIC")
            .jurorCount(0)
            .build());

        postOpt.ifPresent(post -> {
            if (post.getId() != null) {
                markSeen(persona, post.getId(), true);
                writeHistory(persona, "posts", body, post.getId(), category);
                // AI Learning: 합격한 글 예시 뱅크에 저장
                aiLearningClient.saveAsync(body, "POST", category, "SELF_GENERATED");
                logAction(persona, action, "POSTED", corrId,
                    java.util.Map.of("postId", post.getId(), "category", category, "usedLlm", true));
            }
        });
        if (postOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "post_failed", "usedLlm", true));
        }
    }

    // ── Register Resolver (문체 → register 변환) ──────────────────────────────

    /** formality → register 변환. polite=>"polite", 기본="casual" */
    private String resolveRegister(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return null;
        Object formality = vp.get("formality");
        if ("polite".equalsIgnoreCase(String.valueOf(formality))) return "polite";
        return "casual";
    }

    // ── Voice Profile Blocks (Phase 1b) ──────────────────────────────────────

    /** 글 생성용 voice 블록: general_style + example_post_openers + writing_quirks + lexicon + age/political notes */
    private String voiceBlockForPost(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "일반 커뮤니티 사용자";
        StringBuilder sb = new StringBuilder();
        appendStr(sb, vp, "general_style");
        appendExamples(sb, vp, "example_post_openers", "글 시작 예시", 2);
        appendWritingQuirks(sb, vp);
        appendLexicon(sb, vp);
        appendStr(sb, vp, "age_voice_notes", "\n[연령 말투] ");
        appendStr(sb, vp, "political_voice_notes", "\n[성향 표현] ");
        return sb.toString().trim();
    }

    // ── 첨삭 학습 규칙 주입 헬퍼 ──────────────────────────────────────────────

    /**
     * 이 페르소나의 voice_profile.correction_cautions 중 active=true 항목을 "- …" 목록으로 반환.
     * 없거나 비어있으면 null 반환 → PromptAssembler에서 섹션 생략.
     */
    @SuppressWarnings("unchecked")
    private String cautionsBlock(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return null;
        Object raw = vp.get("correction_cautions");
        if (!(raw instanceof List)) return null;
        List<Object> list = (List<Object>) raw;
        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            if (item instanceof Map) {
                Map<String, Object> entry = (Map<String, Object>) item;
                Object active = entry.get("active");
                if (Boolean.TRUE.equals(active)) {
                    Object text = entry.get("text");
                    if (text != null && !text.toString().isBlank()) {
                        sb.append("- ").append(text.toString().trim()).append("\n");
                    }
                }
            }
        }
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    /**
     * 전역 금지 규칙(scope=scope 또는 ALL, active=true)을 "- …" 목록으로 반환.
     * 5분 TTL 캐시 적용. 없거나 비어있으면 null 반환.
     */
    private String globalRulesBlock(String scope) {
        long now = System.currentTimeMillis();
        List<AiGlobalRule> rules = globalRulesCache.get();
        if (rules == null || (now - globalRulesCachedAt) > GLOBAL_RULES_TTL_MS) {
            try {
                // scope는 'POST' 또는 'COMMENT' — 둘 다 'ALL'도 포함하는 쿼리
                rules = aiGlobalRuleRepository.findActiveByScope(scope);
                globalRulesCache.set(rules);
                globalRulesCachedAt = now;
            } catch (Exception e) {
                log.warn("[ActionExecutor] globalRulesBlock DB 조회 실패 (non-critical): {}", e.getMessage());
                rules = List.of();
            }
        }
        if (rules.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (AiGlobalRule rule : rules) {
            sb.append("- ").append(rule.getRuleText().trim()).append("\n");
        }
        return sb.toString().trim();
    }

    /** 댓글 생성용 voice 블록: general_style + example_comments(stance별) + writing_quirks + lexicon + hot_buttons + notes */
    @SuppressWarnings("unchecked")
    private String voiceBlockForComment(Persona persona, String stance) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "일반 커뮤니티 사용자";
        StringBuilder sb = new StringBuilder();
        appendStr(sb, vp, "general_style");
        appendExamples(sb, vp, "example_comments", "댓글 예시", 3);
        appendWritingQuirks(sb, vp);
        appendLexicon(sb, vp);
        appendHotButtons(sb, vp);
        // Reactions: pick by stance
        Object reactionsObj = vp.get("reactions");
        if (reactionsObj instanceof Map) {
            Map<String, Object> reactions = (Map<String, Object>) reactionsObj;
            String stanceKey = "AUTHOR".equals(stance) ? "agree"
                : "PARTNER".equals(stance) ? "disagree" : "curious";
            Object examples = reactions.get(stanceKey);
            if (examples instanceof List) {
                List<?> list = (List<?>) examples;
                int n = Math.min(2, list.size());
                List<?> shuffled = new ArrayList<>(list);
                Collections.shuffle((List<Object>) shuffled, RNG);
                sb.append("\n[").append(stanceKey).append(" 반응] ");
                for (int i = 0; i < n; i++) sb.append(shuffled.get(i)).append(" / ");
            }
        }
        appendStr(sb, vp, "age_voice_notes", "\n[연령] ");
        appendStr(sb, vp, "political_voice_notes", "\n[성향] ");
        return sb.toString().trim();
    }

    /** 대댓글 생성용 voice 블록: general_style + example_replies + writing_quirks + lexicon + hot_buttons + reactions */
    @SuppressWarnings("unchecked")
    private String voiceBlockForReply(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return "일반 커뮤니티 사용자";
        StringBuilder sb = new StringBuilder();
        appendStr(sb, vp, "general_style");
        appendExamples(sb, vp, "example_replies", "대댓글 예시", 2);
        appendWritingQuirks(sb, vp);
        appendLexicon(sb, vp);
        appendHotButtons(sb, vp);
        // Add all reactions as reference
        Object reactionsObj = vp.get("reactions");
        if (reactionsObj instanceof Map) {
            Map<String, Object> reactions = (Map<String, Object>) reactionsObj;
            Object agree = reactions.get("agree");
            if (agree instanceof List && !((List<?>) agree).isEmpty()) {
                List<?> list = (List<?>) agree;
                sb.append("\n[공감 표현] ").append(list.get(RNG.nextInt(list.size())));
            }
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void appendExamples(StringBuilder sb, Map<String, Object> vp, String key, String label, int maxN) {
        Object raw = vp.get(key);
        if (!(raw instanceof List)) return;
        List<Object> list = new ArrayList<>((List<Object>) raw);
        Collections.shuffle(list, RNG);
        int n = Math.min(maxN, list.size());
        if (n == 0) return;
        sb.append("\n[").append(label).append("] ");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(" / ");
            sb.append(list.get(i));
        }
    }

    private void appendStr(StringBuilder sb, Map<String, Object> vp, String key) {
        Object v = vp.get(key);
        if (v != null && !v.toString().isBlank()) sb.append(v.toString().trim());
    }

    private void appendStr(StringBuilder sb, Map<String, Object> vp, String key, String prefix) {
        Object v = vp.get(key);
        if (v != null && !v.toString().isBlank()) sb.append(prefix).append(v.toString().trim());
    }

    /**
     * writing_quirks 맞춤법/오탈자 패턴을 프롬프트에 주입.
     * consistent_errors(고정 오류) 또는 mobile_typos 여부를 구체적 지시로 변환.
     */
    @SuppressWarnings("unchecked")
    private void appendWritingQuirks(StringBuilder sb, Map<String, Object> vp) {
        Object quirksObj = vp.get("writing_quirks");
        if (!(quirksObj instanceof Map)) return;
        Map<String, Object> quirks = (Map<String, Object>) quirksObj;
        if (quirks.isEmpty()) return;

        sb.append("\n[맞춤법·오타 패턴] ");
        boolean addedAny = false;

        // consistent_errors: 고정 맞춤법 오류
        Object errorsObj = quirks.get("consistent_errors");
        if (errorsObj instanceof List) {
            List<?> errors = (List<?>) errorsObj;
            if (!errors.isEmpty()) {
                List<?> shuffled = new ArrayList<>(errors);
                Collections.shuffle((List<Object>) shuffled, RNG);
                int n = Math.min(1, shuffled.size());
                sb.append(shuffled.get(0));
                addedAny = true;
            }
        }

        // mobile_typos: 모바일 오타 여부
        Object mobileObj = quirks.get("mobile_typos");
        if (mobileObj instanceof Boolean && (Boolean) mobileObj) {
            if (addedAny) sb.append(" / ");
            sb.append("모바일 오타 2~3개 자연스럽게");
            addedAny = true;
        }

        if (!addedAny) {
            // quirks가 있지만 파싱 불가면 아무것도 추가 안 함
            sb.setLength(sb.length() - "[맞춤법·오타 패턴] ".length());
        }
    }

    /**
     * lexicon (말투 습관, 자주 쓰는 표현)을 프롬프트에 주입.
     * signature_phrases 또는 typing_habit을 샘플로 추가.
     */
    @SuppressWarnings("unchecked")
    private void appendLexicon(StringBuilder sb, Map<String, Object> vp) {
        Object lexObj = vp.get("lexicon");
        if (!(lexObj instanceof Map)) return;
        Map<String, Object> lexicon = (Map<String, Object>) lexObj;
        if (lexicon.isEmpty()) return;

        sb.append("\n[자주 쓰는 표현] ");
        boolean addedAny = false;

        // signature_phrases: 대표 표현
        Object phrasesObj = lexicon.get("signature_phrases");
        if (phrasesObj instanceof List) {
            List<?> phrases = (List<?>) phrasesObj;
            if (!phrases.isEmpty()) {
                List<?> shuffled = new ArrayList<>(phrases);
                Collections.shuffle((List<Object>) shuffled, RNG);
                int n = Math.min(2, shuffled.size());
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(" / ");
                    sb.append(shuffled.get(i));
                }
                addedAny = true;
            }
        }

        // typing_habit: 타이핑 습관 설명
        if (!addedAny) {
            Object habitObj = lexicon.get("typing_habit");
            if (habitObj != null && !habitObj.toString().isBlank()) {
                sb.append(habitObj.toString().trim());
                addedAny = true;
            }
        }

        if (!addedAny) {
            // lexicon이 있지만 파싱 불가면 아무것도 추가 안 함
            sb.setLength(sb.length() - "[자주 쓰는 표현] ".length());
        }
    }

    /**
     * hot_buttons (감정 트리거/민감 주제)를 프롬프트에 주입.
     * 댓글/대댓글 톤에 영향을 주는 민감 포인트를 간략히 표시.
     */
    @SuppressWarnings("unchecked")
    private void appendHotButtons(StringBuilder sb, Map<String, Object> vp) {
        Object buttonsObj = vp.get("hot_buttons");
        if (!(buttonsObj instanceof Map)) return;
        Map<String, Object> buttons = (Map<String, Object>) buttonsObj;
        if (buttons.isEmpty()) return;

        // triggers: 발끈하는 주제
        Object triggersObj = buttons.get("triggers");
        if (triggersObj instanceof List) {
            List<?> triggers = (List<?>) triggersObj;
            if (!triggers.isEmpty()) {
                sb.append("\n[민감 주제] ");
                List<?> shuffled = new ArrayList<>(triggers);
                Collections.shuffle((List<Object>) shuffled, RNG);
                int n = Math.min(2, shuffled.size());
                for (int i = 0; i < n; i++) {
                    if (i > 0) sb.append(" / ");
                    sb.append(shuffled.get(i));
                }
            }
        }
    }

    // ── 글 길이 다양화 ────────────────────────────────────────────────────────

    /**
     * 페르소나 tier에 따른 가중 랜덤 길이 티어 선택.
     * SHORT=25%, MEDIUM=35%, LONG=25%, VERYLONG=15%
     * HEAVY 페르소나는 LONG+VERYLONG 비중 2배
     */
    private String pickLengthTier(Persona persona) {
        double r = RNG.nextDouble();
        boolean heavy = "HEAVY".equals(persona.getTier());
        if (r < 0.25) return "SHORT";
        if (r < 0.60) return "MEDIUM";
        if (r < (heavy ? 0.85 : 0.80)) return "LONG";
        return "VERYLONG";
    }

    // ── Phase 2c: ArchetypeCatalog topicSeed ─────────────────────────────────

    private String buildTopicSeed(Persona persona) {
        String category = topCategory(persona);

        // 우선순위 1: 오늘의 크롤 기반 토픽 시드 뱅크
        List<AiLearningClient.DailyTopicItem> dailyTopics = aiLearningClient.fetchDailyTopics(category, 5);
        if (!dailyTopics.isEmpty()) {
            // least-used 상위 2개 중 랜덤 선택으로 로테이션
            int pickIdx = RNG.nextInt(Math.min(2, dailyTopics.size()));
            AiLearningClient.DailyTopicItem chosen = dailyTopics.get(pickIdx);
            aiLearningClient.markTopicUsed(chosen.getId());
            log.debug("buildTopicSeed: daily topic id={} category={} persona={}", chosen.getId(), category, persona.getId());
            return chosen.getText();
        }

        // 우선순위 2: 학습 off 또는 오늘 시드 없음 → archetype 기반 fallback
        log.debug("buildTopicSeed: no daily topics for category={}, using archetype fallback", category);
        String archetypeId = persona.getArchetype();
        ArchetypeCatalog.Archetype arch = archetypeCatalog.get(archetypeId)
            .orElseGet(() -> archetypeCatalog.byCategory(category).orElse(null));
        if (arch == null) return null;
        return archetypeCatalog.buildTopicSeed(arch, RNG);
    }

    // ── Phase 2d: ArchetypeCatalog comment samples ────────────────────────────

    private String buildArchetypeCommentSamples(Persona persona, PostDto post) {
        if (post == null || post.getCategory() == null) return null;
        // Use post category to find matching archetype
        String archetypeId = persona.getArchetype();
        ArchetypeCatalog.Archetype arch = archetypeCatalog.get(archetypeId)
            .orElseGet(() -> archetypeCatalog.byCategory(post.getCategory()).orElse(null));
        if (arch == null) return null;
        String political = voiceProfileField(persona, "political_orientation");
        return archetypeCatalog.buildCommentSamples(arch, political);
    }

    // ── Phase 3: demographic ──────────────────────────────────────────────────

    private String demographicStr(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null) return null;
        String age = toKoreanAge(voiceProfileField(persona, "age"));
        String gender = toKoreanGender(voiceProfileField(persona, "gender"));
        String political = toKoreanPolitical(voiceProfileField(persona, "political_orientation"));
        List<String> parts = new ArrayList<>();
        if (age != null) parts.add(age);
        if (gender != null) parts.add(gender);
        if (political != null) parts.add(political + " 성향");
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private String toKoreanAge(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase().trim()) {
            case "20s", "20s_early" -> "20대 초반";
            case "20s_late" -> "20대 후반";
            case "30s", "30s_early" -> "30대 초반";
            case "30s_late" -> "30대 후반";
            case "40s" -> "40대";
            case "50s" -> "50대";
            default -> null;
        };
    }

    private String toKoreanGender(String raw) {
        if (raw == null) return null;
        return switch (raw.toUpperCase().trim()) {
            case "M", "MALE" -> "남성";
            case "F", "FEMALE" -> "여성";
            default -> null;
        };
    }

    private String toKoreanPolitical(String raw) {
        if (raw == null) return null;
        return switch (raw.toLowerCase().trim()) {
            case "conservative" -> "보수";
            case "progressive" -> "진보";
            case "moderate" -> "중도";
            default -> null;
        };
    }

    // ── 피기백 반응 지원 타입 ──────────────────────────────────────────────────

    private record CommentContext(String promptList, List<ReactableItem> items) {}
    private record ReactableItem(long commentId, String authorId) {}

    // ── Phase 4a: 댓글 조회 + 피기백 반응 대상 목록 ──────────────────────────

    /**
     * 게시글의 댓글+대댓글을 표시순으로 평탄화해 번호 매긴 목록과 ReactableItem 목록으로 반환.
     * 번호(1-based)가 likeComments JSON 배열의 인덱스와 1:1 대응한다.
     */
    private CommentContext fetchReactableComments(String postId) {
        try {
            List<CommentThreadDto> comments = backendBot.getComments(postId, 0, 5);
            if (comments == null || comments.isEmpty()) return new CommentContext(null, Collections.emptyList());
            StringBuilder sb = new StringBuilder();
            List<ReactableItem> items = new ArrayList<>();
            int idx = 1;
            for (CommentThreadDto c : comments) {
                if (c.getId() == null || c.getBody() == null || c.getBody().isBlank()) continue;
                sb.append(idx).append(". ").append(truncate(c.getBody(), 60)).append("\n");
                items.add(new ReactableItem(c.getId(), c.getAuthorId()));
                idx++;
                if (c.getReplies() != null) {
                    for (CommentThreadDto r : c.getReplies()) {
                        if (r.getId() == null || r.getBody() == null || r.getBody().isBlank()) continue;
                        sb.append(idx).append(". ↳ ").append(truncate(r.getBody(), 50)).append("\n");
                        items.add(new ReactableItem(r.getId(), r.getAuthorId()));
                        idx++;
                        if (idx > 8) break;
                    }
                }
                if (idx > 8) break;
            }
            return new CommentContext(sb.length() > 0 ? sb.toString().trim() : null, items);
        } catch (Exception e) {
            log.debug("fetchReactableComments failed for post {}: {}", postId, e.getMessage());
            return new CommentContext(null, Collections.emptyList());
        }
    }

    // ── 피기백 반응 디스패치 ──────────────────────────────────────────────────

    /**
     * LLM이 반환한 reactionsJson을 파싱해 vote·likePost·likeComments를 순서대로 디스패치.
     * 파싱/디스패치 실패는 debug 로그 후 no-op (graceful degrade — 본문에 영향 없음).
     */
    private void dispatchReactions(Persona persona, String jwt, PostDto post, String reactionsJson,
                                   List<ReactableItem> items, String corrId) {
        if (reactionsJson == null || reactionsJson.isBlank()) return;
        try {
            String json = extractReactJson(reactionsJson);
            if (json == null) return;
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
            String postId = post != null ? post.getId() : null;

            // 1. vote
            String voteSide = node.path("vote").asText("none");
            if (postId != null && !"none".equalsIgnoreCase(voteSide)) {
                Long optionId = resolveVoteOption(post, voteSide);
                if (optionId != null) {
                    boolean ok = backendBot.vote(jwt, postId, optionId);
                    logPiggyback(persona, corrId, "VOTE", postId, null, ok);
                }
            }

            // 2. likePost
            if (postId != null && node.path("likePost").asBoolean(false)) {
                boolean ok = backendBot.likePost(jwt, postId);
                logPiggyback(persona, corrId, "LIKE", postId, null, ok);
            }

            // 3. likeComments
            com.fasterxml.jackson.databind.JsonNode likeArr = node.path("likeComments");
            if (likeArr.isArray() && !items.isEmpty()) {
                Set<Long> dedup = new HashSet<>();
                for (com.fasterxml.jackson.databind.JsonNode numNode : likeArr) {
                    int num = numNode.asInt(0);
                    if (num < 1 || num > items.size()) continue;
                    ReactableItem item = items.get(num - 1);
                    if (dedup.contains(item.commentId())) continue;
                    // 자기 댓글 좋아요 방지
                    if (item.authorId() != null && persona.getId().equals(item.authorId())) continue;
                    dedup.add(item.commentId());
                    boolean ok = backendBot.likeComment(jwt, postId != null ? postId : "", item.commentId());
                    logPiggyback(persona, corrId, "COMMENT_LIKE", postId, item.commentId(), ok);
                }
            }
        } catch (Exception e) {
            log.debug("dispatchReactions failed corr={}: {}", corrId, e.getMessage());
        }
    }

    private void logPiggyback(Persona persona, String corrId, String type, String postId, Long commentId, boolean ok) {
        try {
            Map<String, Object> detail = new HashMap<>();
            detail.put("via", "piggyback");
            detail.put("usedLlm", false);
            if (postId != null) detail.put("postId", postId);
            if (commentId != null) detail.put("commentId", commentId);
            String targetType = "COMMENT_LIKE".equals(type) ? "COMMENT" : "POST";
            String targetId = commentId != null ? String.valueOf(commentId) : postId;
            actionLogRepo.save(PersonaActionLog.builder()
                .personaId(persona.getId())
                .actionType(type)
                .targetType(targetType)
                .targetId(targetId)
                .usedLlm(false)
                .status(ok ? "POSTED" : "FAILED")
                .correlationId(corrId)
                .detail(detail)
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.debug("logPiggyback failed: {}", e.getMessage());
        }
    }

    /** 작성자/상대방 라벨 → voteOptionId 변환. voteOptions<2개 또는 라벨 없으면 null. */
    private Long resolveVoteOption(PostDto post, String side) {
        if (post == null || post.getVoteOptions() == null || post.getVoteOptions().size() < 2) return null;
        String label = "author".equalsIgnoreCase(side) ? "작성자" : "상대방";
        return post.getVoteOptions().stream()
            .filter(o -> label.equals(o.getLabel()))
            .map(PostDto.VoteOptionDto::getId)
            .findFirst()
            .orElse(null);
    }

    /** 페르소나 좋아요/투표 성향 수치 문자열 (LLM 프롬프트 주입용). */
    private String dispositionNote(Persona persona) {
        double like = voiceScoreLocal(persona, "like_score", 0.45);
        double vote = voiceScoreLocal(persona, "vote_score", 0.30);
        return String.format("좋아요 성향 %.1f/1.0, 투표 성향 %.1f/1.0", like, vote);
    }

    private double voiceScoreLocal(Persona persona, String key, double fallback) {
        try {
            if (persona.getVoiceProfile() == null) return fallback;
            Object v = persona.getVoiceProfile().get(key);
            if (v instanceof Number) return Math.max(0.05, Math.min(0.95, ((Number) v).doubleValue()));
        } catch (Exception ignored) {}
        return fallback;
    }

    /** reactionsJson 문자열에서 첫 { … 끝 } JSON 추출. */
    private String extractReactJson(String raw) {
        if (raw == null) return null;
        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        return (s >= 0 && e > s) ? raw.substring(s, e + 1) : null;
    }

    // ── Phase 4b: Reply stance diversification ────────────────────────────────

    /** AGREE/DISAGREE/CURIOUS 가중 랜덤 — persona bias 활용 */
    private String pickReplyStance(Persona persona) {
        double bias = 0.0;
        Map<String, Double> bp = persona.getBiasProfile();
        if (bp != null && !bp.isEmpty()) {
            bias = bp.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }
        // bias >0: AGREE 선호, <0: DISAGREE 선호, 기본: CURIOUS
        double agreeProb = 0.5 + bias * 0.3;
        agreeProb = Math.max(0.1, Math.min(0.8, agreeProb));
        double disagreeProb = 0.5 - bias * 0.3;
        disagreeProb = Math.max(0.1, Math.min(0.8, disagreeProb));
        double r = RNG.nextDouble() * (agreeProb + disagreeProb + 0.3);
        if (r < agreeProb) return "AGREE";
        if (r < agreeProb + disagreeProb) return "DISAGREE";
        return "CURIOUS";
    }

    // ── Phase 4c: Stance weighted sampling (댓글 stance) ─────────────────────

    /** 댓글 stance 무작위 가중 샘플링 — bias ±0.2 임계 → 확률적 */
    private String pickStanceWeighted(Persona persona, PostDto post) {
        if (post == null || post.getCategory() == null) return randomNeutralOrBias(0.0);
        double bias = Optional.ofNullable(persona.getBiasProfile())
            .map(bp -> bp.getOrDefault(post.getCategory(), 0.0))
            .orElse(0.0);
        return randomNeutralOrBias(bias);
    }

    private String randomNeutralOrBias(double bias) {
        // 0.5 + bias/2 → AUTHOR probability (clamped 0.05-0.95)
        double authorProb = Math.max(0.05, Math.min(0.95, 0.5 + bias / 2.0));
        double partnerProb = Math.max(0.05, Math.min(0.95, 0.5 - bias / 2.0));
        double neutralProb = 1.0 - Math.abs(bias) * 0.6;
        neutralProb = Math.max(0.1, neutralProb);
        double total = authorProb + partnerProb + neutralProb;
        double r = RNG.nextDouble() * total;
        if (r < authorProb) return "AUTHOR";
        if (r < authorProb + partnerProb) return "PARTNER";
        return "NEUTRAL";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String voiceProfileField(Persona persona, String key) {
        try {
            if (persona.getVoiceProfile() == null) return null;
            Object v = persona.getVoiceProfile().get(key);
            return v != null ? v.toString() : null;
        } catch (Exception e) { return null; }
    }

    private String voiceFormality(Persona persona) {
        String f = voiceProfileField(persona, "formality");
        return f != null ? f : "casual";
    }

    private void writeHistory(Persona persona, String type, String content, String postId, String category) {
        try {
            String email = botEmail(persona);
            String profileName = email.contains("@") ? email.split("@")[0] : persona.getId();
            java.nio.file.Path dir = java.nio.file.Paths.get(historyDir, profileName, "history");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(type + ".md");

            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneId.of("Asia/Seoul"))
                .format(java.time.Instant.now());

            String entry;
            if ("posts".equals(type)) {
                String titlePreview = content.length() > 40 ? content.substring(0, 40) + "..." : content;
                entry = String.format("\n| %s | %s | %s | %s | POSTED |\n\n### %s — %s\n%s\n\n---\n",
                    timestamp, category != null ? category : "-", postId != null ? postId : "-",
                    titlePreview, timestamp, category != null ? category : "-", content);
            } else {
                String preview = content.length() > 60 ? content.substring(0, 60) + "..." : content;
                entry = String.format("\n| %s | %s | %s | %s |\n\n> %s\n\n---\n",
                    timestamp, "댓글", postId != null ? postId : "-", preview, content);
            }
            java.nio.file.Files.writeString(file, entry,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.debug("History write failed for persona {} type {}: {}", persona.getId(), type, e.getMessage());
        }
    }

    // ── 댓글 모드·길이 샘플링 (문체 현실화 S3) ───────────────────────────────
    // "공감→경험담→조언" 획일 구조 해체 — 매 댓글마다 반응 모드를 확률 선택.

    enum CommentMode { REACTION_ONLY, SHORT_AGREE, QUESTION, DISAGREE, EXPERIENCE, ADVICE, TANGENT }

    /** 페르소나 성향(slang·formality)·stance 반영 가중 랜덤. 테스트를 위해 package-private. */
    CommentMode pickCommentMode(Persona persona, String stance) {
        double slang = persona.getSlangLevel() != null ? persona.getSlangLevel().doubleValue() : 0.3;
        boolean polite = "polite".equalsIgnoreCase(voiceFormality(persona));

        // 기본 가중치 — 초단문 모드(반응만+동조+사족) 합산 ~37%
        java.util.EnumMap<CommentMode, Double> w = new java.util.EnumMap<>(CommentMode.class);
        w.put(CommentMode.REACTION_ONLY, 0.22);
        w.put(CommentMode.SHORT_AGREE,   0.12);
        w.put(CommentMode.QUESTION,      0.15);
        w.put(CommentMode.DISAGREE,      0.12);
        w.put(CommentMode.EXPERIENCE,    0.18);
        w.put(CommentMode.ADVICE,        0.15);
        w.put(CommentMode.TANGENT,       0.06);

        if (slang >= 0.6) {            // 거친 커뮤 보이스 — 딴지·드립 비중↑
            w.merge(CommentMode.DISAGREE, 0.05, Double::sum);
            w.merge(CommentMode.TANGENT, 0.03, Double::sum);
            w.merge(CommentMode.EXPERIENCE, -0.06, Double::sum);
        }
        if (polite) {                  // 존댓말 페르소나 — 경험담·조언형 비중↑, 드립↓
            w.merge(CommentMode.EXPERIENCE, 0.06, Double::sum);
            w.merge(CommentMode.ADVICE, 0.04, Double::sum);
            w.put(CommentMode.TANGENT, 0.01);
            w.merge(CommentMode.REACTION_ONLY, -0.05, Double::sum);
        }
        if ("PARTNER".equalsIgnoreCase(stance)) {  // 상대방 편 — 반박 결이 자연스러움
            w.merge(CommentMode.DISAGREE, 0.10, Double::sum);
            w.merge(CommentMode.REACTION_ONLY, -0.05, Double::sum);
        }

        double total = w.values().stream().mapToDouble(v -> Math.max(0, v)).sum();
        double r = RNG.nextDouble() * total;
        for (java.util.Map.Entry<CommentMode, Double> e : w.entrySet()) {
            r -= Math.max(0, e.getValue());
            if (r <= 0) return e.getKey();
        }
        return CommentMode.EXPERIENCE;
    }

    /** 모드 → 프롬프트 지시문 (PromptAssembler가 고정 "50~150자" 대신 그대로 렌더). */
    String commentModeHint(CommentMode mode) {
        return switch (mode) {
            case REACTION_ONLY -> "반응만: 감정 한 줄만 툭 던지기 — 조언·경험담·질문 금지, 10~30자";
            case SHORT_AGREE   -> "짧은 동조: 한마디로 맞장구만 — 10~25자";
            case QUESTION      -> "되묻기: 궁금한 점 딱 하나만 물어보기 — 조언 금지, 15~40자";
            case DISAGREE      -> "딴지: 글쓴이와 살짝 다른 시각이나 반박 한 줄 — 사과·완곡어 없이, 20~60자";
            case EXPERIENCE    -> "경험담: 내 비슷한 경험만 풀기 — 조언으로 마무리하지 말 것, 40~120자";
            case ADVICE        -> "훈수: 결론부터 단호하게 한마디 — 공감 인사 생략, 20~60자";
            case TANGENT       -> "사족: 본문에서 살짝 어긋난 혼잣말·드립 한 줄 — 10~30자";
        };
    }

    /** 대댓글 길이 2단 샘플링 — 획일한 15~40자 대신 초단문 위주로 분산. */
    String replyLengthHint() {
        return RNG.nextDouble() < 0.6
            ? "초단문: 8~25자 한마디만 (한 문장도 안 됨)"
            : "짧게: 25~60자 (최대 두 마디)";
    }

    // ── 문체 앵커 샘플링 (문체 현실화 S2) ────────────────────────────────────

    /** voice_type(NATEPAN 등) → example_bank 크롤 source 매핑. 매핑 없으면 null=전체 크롤 소스. */
    private static final java.util.Map<String, String> VOICE_SOURCE = java.util.Map.of(
        "NATEPAN", "natepan", "DCINSIDE", "dcinside", "BLIND", "blind",
        "FMKOREA", "fmkorea", "THEQOO", "theqoo", "CLIEN", "clien",
        "PPOMPPU", "ppomppu", "RULIWEB", "ruliweb", "MLBPARK", "mlbpark");

    /** 페르소나 voice 소스·레지스터 기반 문체 few-shot ("---" 구분). 없으면 null. */
    private String styleExamplesFor(Persona persona, String contentType, int topK, int maxLen) {
        try {
            String voiceType = voiceProfileField(persona, "voice_type");
            String source = voiceType != null ? VOICE_SOURCE.get(voiceType.trim().toUpperCase()) : null;
            java.util.List<AiLearningClient.ExampleItem> items =
                aiLearningClient.styleSample(source, contentType, resolveRegister(persona), topK, maxLen);
            if (items.isEmpty()) return null;
            return items.stream()
                .map(AiLearningClient.ExampleItem::getContent)
                .collect(java.util.stream.Collectors.joining("\n---\n"));
        } catch (Exception e) {
            log.debug("styleExamplesFor failed for persona {}: {}", persona.getId(), e.getMessage());
            return null;
        }
    }

    // ── 최근 출력 히스토리 — 반복 방지 (문체 현실화 S1) ──────────────────────

    /**
     * 히스토리 파일에서 이 페르소나의 최근 본문 n개 추출 (오래된 → 최신 순).
     * writeHistory 포맷 역파싱: 엔트리 구분 "---", comments=첫 "> " 이후, posts="### …" 헤더 다음 줄부터.
     * 실패 시 빈 리스트 — 생성은 계속 진행.
     */
    private java.util.List<String> loadRecentBodies(Persona persona, String type, int n) {
        try {
            String email = botEmail(persona);
            String profileName = email.contains("@") ? email.split("@")[0] : persona.getId();
            java.nio.file.Path file = java.nio.file.Paths.get(historyDir, profileName, "history", type + ".md");
            if (!java.nio.file.Files.exists(file)) return java.util.List.of();
            String raw = java.nio.file.Files.readString(file);
            java.util.List<String> bodies = new java.util.ArrayList<>();
            for (String block : raw.split("\\n---")) {
                String body = extractHistoryBody(block, type);
                if (body != null && !body.isBlank()) bodies.add(body);
            }
            int from = Math.max(0, bodies.size() - n);
            return new java.util.ArrayList<>(bodies.subList(from, bodies.size()));
        } catch (Exception e) {
            log.debug("loadRecentBodies failed for persona {} type {}: {}", persona.getId(), type, e.getMessage());
            return java.util.List.of();
        }
    }

    /** writeHistory 엔트리 블록에서 본문만 추출. 매칭 실패 시 null. */
    static String extractHistoryBody(String block, String type) {
        if (block == null || block.isBlank()) return null;
        if ("posts".equals(type)) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^### [^\\n]*\\n").matcher(block);
            return m.find() ? block.substring(m.end()).trim() : null;
        }
        // comments: 본문은 "> "로 시작 (본문 내 개행은 접두사 없이 이어짐)
        int idx = block.indexOf("\n> ");
        if (idx >= 0) return block.substring(idx + 3).trim();
        return block.stripLeading().startsWith("> ") ? block.stripLeading().substring(2).trim() : null;
    }

    /** 최근 본문들 → 프롬프트 주입용 "- …" 목록 (최신이 위, 각 항목 eachMaxLen 컷). 없으면 null. */
    static String formatRecentOutputs(java.util.List<String> bodies, int eachMaxLen) {
        if (bodies == null || bodies.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = bodies.size() - 1; i >= 0; i--) {
            String b = bodies.get(i).replaceAll("\\s+", " ").trim();
            if (b.length() > eachMaxLen) b = b.substring(0, eachMaxLen) + "…";
            sb.append("- ").append(b).append('\n');
        }
        return sb.toString().trim();
    }

    /** 반복 가드 재시도용 피드백 — 기존 recentOutputs에 직전 실패 시도를 덧붙임. */
    static String repetitionRetryFeedback(String recentOutputs, String failedDraft, String label) {
        String draft = failedDraft.replaceAll("\\s+", " ").trim();
        if (draft.length() > 150) draft = draft.substring(0, 150) + "…";
        return (recentOutputs != null ? recentOutputs + "\n" : "")
            + "- [직전 시도 — 반려됨] " + draft + "\n"
            + "→ 직전 시도가 위 최근 " + label + "과 거의 똑같았다. 시작 문구·어휘·전개를 전부 바꿔서 완전히 다르게 다시 쓸 것";
    }

    /** 생성문 vs 최근 출력들의 문자 2-gram Jaccard 최대값 (0.0~1.0). */
    static double maxBigramJaccard(String text, java.util.List<String> recents) {
        java.util.Set<String> a = charBigrams(text);
        if (a.isEmpty() || recents == null) return 0.0;
        double max = 0.0;
        for (String r : recents) {
            java.util.Set<String> b = charBigrams(r);
            if (b.isEmpty()) continue;
            int inter = 0;
            for (String g : a) if (b.contains(g)) inter++;
            int union = a.size() + b.size() - inter;
            double j = union > 0 ? (double) inter / union : 0.0;
            if (j > max) max = j;
        }
        return max;
    }

    /** 공백 제거 후 문자 2-gram 집합. 12자 미만 초단문은 노이즈가 커서 빈 셋 반환(가드 제외). */
    static java.util.Set<String> charBigrams(String text) {
        if (text == null) return java.util.Set.of();
        String t = text.replaceAll("\\s+", "");
        if (t.length() < 12) return java.util.Set.of();
        java.util.Set<String> grams = new java.util.HashSet<>();
        for (int i = 0; i < t.length() - 1; i++) grams.add(t.substring(i, i + 2));
        return grams;
    }

    private void markSeen(Persona persona, String postId, boolean acted) {
        if (postId == null) return;
        try {
            if (!seenPostRepo.existsByPersonaIdAndPostId(persona.getId(), postId)) {
                seenPostRepo.save(PersonaSeenPost.builder()
                    .personaId(persona.getId())
                    .postId(postId)
                    .seenAt(Instant.now())
                    .acted(acted)
                    .build());
            }
        } catch (Exception e) {
            log.warn("markSeen failed for persona={} post={}: {}", persona.getId(), postId, e.getMessage());
        }
    }

    private void logAction(Persona persona, PlannedAction action, String status, String corrId, java.util.Map<String, java.lang.Object> detail) {
        try {
            boolean usedLlm = (boolean) detail.getOrDefault("usedLlm", false);
            String targetId = action.targetPost() != null ? action.targetPost().getId() : null;
            if (targetId == null && action.parentCommentId() != null) {
                targetId = java.lang.String.valueOf(action.parentCommentId());
            }
            String targetType = switch (action.type()) {
                case LIKE, VOTE, COMMENT, POST, VIEW -> "POST";
                case REPLY, INVITE_ANSWER, COMMENT_LIKE -> "COMMENT";
            };
            actionLogRepo.save(PersonaActionLog.builder()
                .personaId(persona.getId())
                .actionType(action.type().name())
                .targetType(targetType)
                .targetId(targetId)
                .usedLlm(usedLlm)
                .status(status)
                .correlationId(corrId)
                .detail(detail)
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.warn("logAction failed: {}", e.getMessage());
        }
    }

    private String topCategory(Persona persona) {
        java.util.Map<String, Double> interests = persona.getInterests();
        if (interests == null || interests.isEmpty()) return "OTHER";
        return interests.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse("OTHER");
    }

    /**
     * LLM 메타 텍스트 제거: "[원문 수정본]", "[수정본]", "[제목]", "[본문]" 등
     * 선두 메타 라인(대괄호로 감싼 머리말)과 "수정본:" / "원문:" 접두사 제거.
     * 본문 중간의 정상 대괄호(이모지/강조)는 보존.
     */
    private String cleanLlmMetaText(String body) {
        if (body == null || body.isBlank()) return body;

        String[] lines = body.split("[\\n\\r]+");
        StringBuilder result = new StringBuilder();
        boolean skipLeadingMeta = true;

        for (String line : lines) {
            String trimmed = line.trim();

            if (skipLeadingMeta) {
                // 선두 메타 라인 패턴: "[...]" 시작 또는 "수정본:" / "원문:" 접두
                if (trimmed.matches("^\\[.*\\]\\s*$") ||
                    trimmed.startsWith("수정본:") || trimmed.startsWith("원문:") ||
                    trimmed.startsWith("제목:") || trimmed.startsWith("본문:")) {
                    continue;  // 이 라인은 스킵
                }
                // 첫 실제 콘텐츠를 만나면 이후로 메타 스킵 중지
                if (!trimmed.isEmpty()) {
                    skipLeadingMeta = false;
                }
            }

            if (!skipLeadingMeta) {
                if (result.length() > 0) result.append("\n");
                result.append(line);
            }
        }

        return result.toString().trim();
    }

    private String extractTitle(String body) {
        if (body == null) return "갈등 사연";
        String[] lines = body.split("[\\n\\r]+");

        // 메타 라인 건너뛰고 첫 실제 문장을 제목으로
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.matches("^\\[.*\\]\\s*$") &&
                !trimmed.startsWith("수정본:") && !trimmed.startsWith("원문:") &&
                !trimmed.startsWith("제목:") && !trimmed.startsWith("본문:")) {
                return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
            }
        }

        return "갈등 사연";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String botEmail(Persona persona) {
        return emailCache.computeIfAbsent(persona.getId(), id -> {
            try {
                String email = jdbcTemplate.queryForObject(
                    "SELECT email FROM users WHERE id = ?", String.class, id);
                return email != null ? email : "unknown@againspring.com";
            } catch (Exception e) {
                return "unknown@againspring.com";
            }
        });
    }
}
