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
            default -> log.warn("Unhandled action type: {}", action.type());
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

        // RAG: AiLearningClient 동적 예시 검색
        String dynamicExamples = "";
        java.util.List<AiLearningClient.ExampleItem> examples = aiLearningClient.findSimilar(
            postExcerpt, "COMMENT", action.targetPost().getCategory(), 3);
        if (!examples.isEmpty()) {
            dynamicExamples = examples.stream()
                .map(AiLearningClient.ExampleItem::getContent)
                .collect(java.util.stream.Collectors.joining("\n---\n"));
            log.debug("RAG: {} comments found corr={}", examples.size(), corrId);
        }

        java.util.Optional<LlmAiUserClient.GenResult> resultOpt = llmClient.generateCommentR(GenDto.CommentRequest.builder()
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
            .dynamicExamples(dynamicExamples)
            .correctionCautions(cautionsBlock(persona))
            .globalForbidRules(globalRulesBlock("COMMENT"))
            .correlationId(corrId)
            .backend(backendFor("COMMENT"))
            .build());

        if (resultOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        LlmAiUserClient.GenResult res = resultOpt.get();
        String text = res.text();
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(text, ContentSafetyGuard.ContentType.COMMENT);
        if (!guard.passed()) {
            log.warn("Comment blocked for persona {} post {}: {}", persona.getId(), postId, guard.reason());
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        boolean ok = backendBot.addComment(jwt, postId, text, null);
        markSeen(persona, postId, true);
        if (ok) {
            writeHistory(persona.getId(), "comments", text, postId, null);
            // AI Learning: 합격한 댓글 예시 뱅크에 저장
            aiLearningClient.saveAsync(text, "COMMENT",
                action.targetPost() != null ? action.targetPost().getCategory() : "OTHER", "SELF_GENERATED");
            // 피기백 반응 디스패치 (추가 LLM 호출 0건)
            dispatchReactions(persona, jwt, action.targetPost(), res.reactionsJson(), ctx.items(), corrId);
        }
        logAction(persona, action, ok ? "POSTED" : "FAILED", corrId,
            java.util.Map.of("postId", postId, "len", text.length(), "usedLlm", true));
    }

    private void executeReply(Persona persona, PlannedAction action, String jwt, String corrId) {
        if (action.targetPost() == null || action.parentCommentId() == null) return;
        String postId = action.targetPost().getId();

        // Phase 4b: 대댓글 stance 다양화 (CURIOUS 하드코딩 제거)
        String stance = pickReplyStance(persona);

        // Phase 4b: 원글 발췌 + 형제 댓글 (PlannedAction에서)
        String postBodyExcerpt = action.targetPost().getBodyPublished();
        String siblingComments = action.siblingComments();

        java.util.Optional<LlmAiUserClient.GenResult> resultOpt = llmClient.generateReplyR(GenDto.ReplyRequest.builder()
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
            .build());

        if (resultOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        LlmAiUserClient.GenResult res = resultOpt.get();
        String text = res.text();
        ContentSafetyGuard.GuardResult guard = safetyGuard.check(text, ContentSafetyGuard.ContentType.COMMENT);
        if (!guard.passed()) {
            logAction(persona, action, "BLOCKED", corrId, java.util.Map.of("reason", guard.reason(), "usedLlm", true));
            return;
        }
        boolean ok = backendBot.addComment(jwt, postId, text, action.parentCommentId());
        if (ok) {
            writeHistory(persona.getId(), "comments", text, postId, null);
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

        // AI Learning: 동적 예시 검색 및 주입 (RAG)
        String dynamicExamples = "";
        java.util.List<AiLearningClient.ExampleItem> examples = aiLearningClient.findSimilar(
            topicSeed,
            "POST",
            category,
            3
        );
        if (!examples.isEmpty()) {
            dynamicExamples = examples.stream()
                .map(e -> e.getContent())
                .collect(java.util.stream.Collectors.joining("\n---\n"));
            log.debug("RAG: {} posts found for {}", examples.size(), corrId);
        }

        java.util.Optional<String> bodyOpt = llmClient.generatePost(GenDto.PostRequest.builder()
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
            .build());

        if (bodyOpt.isEmpty()) {
            logAction(persona, action, "FAILED", corrId, java.util.Map.of("error", "gen_failed"));
            return;
        }
        String rawBody = bodyOpt.get();
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
                writeHistory(persona.getId(), "posts", body, post.getId(), category);
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
        String archetypeId = persona.getArchetype();
        ArchetypeCatalog.Archetype arch = archetypeCatalog.get(archetypeId)
            .orElseGet(() -> archetypeCatalog.byCategory(topCategory(persona)).orElse(null));
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

    private String writeHistory(String personaId, String type, String content, String postId, String category) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(historyDir, personaId);
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
            log.debug("History write failed for persona {} type {}: {}", personaId, type, e.getMessage());
        }
        return null;
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
