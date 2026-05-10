package com.againspring.service;

import com.againspring.common.exception.GuestLimitException;
import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.llm.bridge.CancelableInvocation;
import com.againspring.llm.bridge.ClaudeCodeBridge;
import com.againspring.llm.bridge.exception.InvocationCanceledException;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.safety.IsolationLintFilter;
import com.againspring.service.context.IssueContextMerger;
import com.againspring.service.context.PhaseDMetrics;
import com.againspring.service.context.QuestionQueueUpdater;
import com.againspring.service.context.UserStateAppender;
import com.againspring.service.crisis.CrisisDetector;
import com.againspring.service.parser.ChatTurnMetaParser;
import com.againspring.service.prompt.ChatPromptAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 새 메시지 도착 시 진행 중 LLM 호출을 취소하고 재호출.
 * POST /messages 응답: <100ms (사용자 메시지 저장만).
 * mediator 응답: 폴링으로 FE에 전달.
 */
@Slf4j
@Service
public class CancelableChatService {

    private final MessageRepository messageRepo;
    private final SessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final ClaudeCodeBridge llmBridge;
    private final CrisisDetector crisisDetector;
    private final ChatPromptAssembler promptAssembler;
    private final SessionStateMachine stateMachine;
    private final ChatTurnMetaParser turnMetaParser;
    private final UserStateAppender userStateAppender;
    private final IssueContextMerger issueContextMerger;
    private final QuestionQueueUpdater questionQueueUpdater;
    private final IsolationLintFilter isolationLintFilter;
    private final PhaseDMetrics phaseDMetrics;
    private final TransactionTemplate transactionTemplate;
    private final com.againspring.config.UserPermissionsConfig permissions;

    // key = sessionId + ":" + sender.name() — A와 B 슬롯 독립 유지
    private final ConcurrentHashMap<String, CancelableInvocation> activeInvocations =
            new ConcurrentHashMap<>();

    public CancelableChatService(
            MessageRepository messageRepo,
            SessionRepository sessionRepo,
            UserRepository userRepo,
            ClaudeCodeBridge llmBridge,
            CrisisDetector crisisDetector,
            ChatPromptAssembler promptAssembler,
            SessionStateMachine stateMachine,
            ChatTurnMetaParser turnMetaParser,
            UserStateAppender userStateAppender,
            IssueContextMerger issueContextMerger,
            QuestionQueueUpdater questionQueueUpdater,
            IsolationLintFilter isolationLintFilter,
            PhaseDMetrics phaseDMetrics,
            PlatformTransactionManager txManager,
            com.againspring.config.UserPermissionsConfig permissions) {
        this.messageRepo = messageRepo;
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.llmBridge = llmBridge;
        this.crisisDetector = crisisDetector;
        this.promptAssembler = promptAssembler;
        this.stateMachine = stateMachine;
        this.turnMetaParser = turnMetaParser;
        this.userStateAppender = userStateAppender;
        this.issueContextMerger = issueContextMerger;
        this.questionQueueUpdater = questionQueueUpdater;
        this.isolationLintFilter = isolationLintFilter;
        this.phaseDMetrics = phaseDMetrics;
        this.transactionTemplate = new TransactionTemplate(txManager);
        this.permissions = permissions;
    }

    /**
     * 사용자 메시지 즉시 저장 — LLM 호출 없이 <100ms 응답.
     * 진행 중 invocation이 있으면 atomic하게 취소.
     * 위기 레벨 1이면 crisisBlocked 반환 (invocation 취소 없음).
     *
     * Bug C 수정: AWAITING_FINALIZATION 상태에서 새 메시지 → 상태 복귀 + 종료 요청 취소
     */
    @Transactional
    public ChatService.ChatTurnResult acceptUserMessage(
            String sessionId, MessageSender sender, String content) {

        Session session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (!stateMachine.isActive(session.getStatus())) {
            throw new IllegalStateException("Session is not active: " + session.getStatus());
        }

        // 게스트 턴 제한 (user-permissions.json: tiers.guest.sessions.messageTurnLimit)
        String msgUserId = sender == MessageSender.USER_A
                ? session.getUserAId() : session.getUserBId();
        if (msgUserId != null) {
            User msgUser = userRepo.findByIdAndDeletedAtIsNull(msgUserId).orElse(null);
            if (msgUser != null && msgUser.isGuest()) {
                Integer turnLimit = permissions.getGuest().getSessions().getMessageTurnLimit();
                if (turnLimit != null) {
                    int count = sender == MessageSender.USER_A
                            ? (session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount())
                            : (session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount());
                    if (count >= turnLimit) {
                        throw new GuestLimitException();
                    }
                }
            }
        }

        var crisis = crisisDetector.detect(content);
        if (crisis.level() == 1) {
            log.warn("Crisis level 1 in session {} — rejecting without invocation change", sessionId);
            return ChatService.ChatTurnResult.crisisBlocked();
        }

        // Bug C: AWAITING_FINALIZATION 상태에서 새 메시지 도착 → 종료 요청 묵시적 거절
        if (session.getStatus() == com.againspring.domain.enums.SessionStatus.AWAITING_FINALIZATION) {
            log.info("Session {} in AWAITING_FINALIZATION received new message — implicitly canceling finalization request",
                    sessionId);
            session.setStatus(com.againspring.domain.enums.SessionStatus.CHATTING_DUO);
            session.setFinalizeAgreedByA(false);
            session.setFinalizeAgreedByB(false);
            sessionRepo.save(session);
        }

        cancelActiveInvocation(sessionId, sender, "new_user_message");

        Message userMsg = messageRepo.save(Message.builder()
                .sessionId(sessionId)
                .sender(sender)
                .content(content)
                .charCount(content.length())
                .crisisLevel(crisis.level() == 2 ? 2 : null)
                .build());
        incrementUserMessageCount(session, sender);

        return ChatService.ChatTurnResult.success(userMsg, List.of(), false);
    }

    /**
     * 새 LLM invocation 시작 (비동기).
     * acceptUserMessage 트랜잭션이 커밋된 후 컨트롤러에서 호출해야 함.
     */
    public void beginInvocation(String sessionId, MessageSender sender) {
        final String[] promptHolder = new String[1];
        transactionTemplate.execute(status -> {
            Session session = sessionRepo.findById(sessionId).orElse(null);
            if (session == null || !stateMachine.isActive(session.getStatus())) return null;

            boolean isDuo = stateMachine.isDuo(session.getStatus());
            List<Message> recentMessages = isDuo
                    ? getRecentMessagesForDuo(sessionId, 20)
                    : getRecentMessagesForSolo(sessionId, sender, 10);

            String lastContent = extractLastUserContent(recentMessages, sender);
            try {
                if (isDuo) {
                    User userA = loadUserSafely(session.getUserAId());
                    User userB = loadUserSafely(session.getUserBId());
                    promptHolder[0] = promptAssembler.assembleDuoTurn(
                            session, userA, userB, sender, lastContent, recentMessages);
                } else {
                    String uid = sender == MessageSender.USER_A
                            ? session.getUserAId() : session.getUserBId();
                    User soloUser = loadUserSafely(uid);
                    promptHolder[0] = promptAssembler.assembleSoloTurn(
                            session, soloUser, lastContent, recentMessages);
                }
            } catch (Exception e) {
                log.error("Prompt assembly failed for session {}", sessionId, e);
                promptHolder[0] = "";
            }
            return null;
        });

        String prompt = promptHolder[0];
        if (prompt == null) {
            log.warn("Session {} not found or inactive — skipping invocation", sessionId);
            return;
        }

        CancelableInvocation inv = llmBridge.invokeCancelable(
                prompt, ChatService.MODEL_HAIKU, sessionId);

        String key = invocationKey(sessionId, sender);
        CancelableInvocation displaced = activeInvocations.put(key, inv);
        if (displaced != null && !displaced.isCanceled()) {
            log.warn("Displaced active invocation in beginInvocation for session {} sender {}", sessionId, sender);
            displaced.cancel();
        }

        inv.getResultFuture().whenComplete((result, error) -> {
            if (activeInvocations.get(key) != inv) {
                log.debug("Invocation {} superseded for session {} sender {}", inv.getInvocationId(), sessionId, sender);
                return;
            }
            try {
                if (error == null) {
                    handleSuccessfulResponse(sessionId, sender, result, inv);
                } else if (!isCancellation(error)) {
                    log.error("LLM failed for session {}: {}", sessionId, error.getMessage());
                    saveFallbackMessage(sessionId, sender.mediatorCounterpart());
                }
            } catch (Exception e) {
                log.error("Callback error for session {}", sessionId, e);
            } finally {
                activeInvocations.remove(key, inv);
            }
        });
    }

    public void cleanupSession(String sessionId) {
        cancelActiveInvocation(sessionId, MessageSender.USER_A, "session_cleanup");
        cancelActiveInvocation(sessionId, MessageSender.USER_B, "session_cleanup");
    }

    // --- private helpers ---

    private static String invocationKey(String sessionId, MessageSender sender) {
        return sessionId + ":" + sender.name();
    }

    private void cancelActiveInvocation(String sessionId, MessageSender sender, String reason) {
        CancelableInvocation existing = activeInvocations.remove(invocationKey(sessionId, sender));
        if (existing != null) {
            log.info("Canceled LLM invocation {} for session {} sender {} (reason={})",
                    existing.getInvocationId(), sessionId, sender, reason);
            existing.cancel();
        }
    }

    private void handleSuccessfulResponse(
            String sessionId, MessageSender sender, String rawResponse, CancelableInvocation inv) {
        transactionTemplate.execute(status -> {
            if (inv.isCanceled()) return null;

            Session session = sessionRepo.findById(sessionId).orElse(null);
            if (session == null || !stateMachine.isActive(session.getStatus())) return null;

            MessageSender mediatorSender = sender.mediatorCounterpart();
            int turnIndex = (session.getHorsemenHistory() == null
                    ? 0 : session.getHorsemenHistory().size()) + 1;

            ChatTurnMetaParser.Result parsed = turnMetaParser.parse(
                    rawResponse, turnIndex, sender.name());
            String mediatorResponse = (parsed.mediatorMessage() == null || parsed.mediatorMessage().isBlank())
                    ? rawResponse : parsed.mediatorMessage();

            appendPsychologyHistory(session, parsed);
            userStateAppender.append(session, parsed.userState());
            issueContextMerger.merge(session, parsed.issueDelta(), turnIndex);
            questionQueueUpdater.update(session, parsed.queueDelta(), turnIndex);

            if (parsed.userState() != null) phaseDMetrics.recordUserState(parsed.userState().state);
            if (parsed.userState() != null || parsed.issueDelta() != null) phaseDMetrics.recordMetaPopulated();
            if (parsed.queueDelta() != null && parsed.queueDelta().asked != null) {
                phaseDMetrics.recordQueueAsked(parsed.queueDelta().asked.size());
            }

            String[] rawChunks = mediatorResponse.split("(?m)^---\\s*$");
            List<String> chunks = new ArrayList<>();
            for (String c : rawChunks) {
                String trimmed = c.strip();
                if (!trimmed.isEmpty()) chunks.addAll(splitLongChunk(trimmed));
            }
            if (chunks.isEmpty()) chunks.add(mediatorResponse);

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                if (isolationLintFilter.violatesIsolation(chunk)) {
                    log.warn("Isolation violation in chunk {} for session {}", i, sessionId);
                    chunk = "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?";
                }
                messageRepo.save(Message.builder()
                        .sessionId(sessionId)
                        .sender(mediatorSender)
                        .content(chunk)
                        .charCount(chunk.length())
                        .llmModel(ChatService.MODEL_HAIKU)
                        .build());
            }

            checkAndTriggerFinalizationSuggestion(session);
            return null;
        });
    }

    private void saveFallbackMessage(String sessionId, MessageSender mediatorSender) {
        transactionTemplate.execute(status -> {
            String fallback = "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?";
            // V11: '-fallback' 접미사로 LLM 호출 실패율 모니터링에서 식별
            messageRepo.save(Message.builder()
                    .sessionId(sessionId)
                    .sender(mediatorSender)
                    .content(fallback)
                    .charCount(fallback.length())
                    .llmModel(ChatService.MODEL_HAIKU + "-fallback")
                    .build());
            return null;
        });
    }

    private boolean isCancellation(Throwable error) {
        return error instanceof InvocationCanceledException
                || (error.getCause() instanceof InvocationCanceledException);
    }

    private String extractLastUserContent(List<Message> messages, MessageSender sender) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getSender() == sender) {
                return messages.get(i).getContent();
            }
        }
        return "";
    }

    private void appendPsychologyHistory(Session session, ChatTurnMetaParser.Result parsed) {
        if (parsed == null) return;
        boolean dirty = false;
        if (parsed.horsemen() != null) {
            List<Session.HorsemenTurnEntry> hist = session.getHorsemenHistory();
            if (hist == null) hist = new ArrayList<>();
            hist.add(parsed.horsemen());
            session.setHorsemenHistory(hist);
            updateEmotionIntensity(session, parsed.horsemen());
            dirty = true;
        }
        if (parsed.nvc() != null) {
            List<Session.NvcTurnEntry> hist = session.getNvcCompletionHistory();
            if (hist == null) hist = new ArrayList<>();
            hist.add(parsed.nvc());
            session.setNvcCompletionHistory(hist);
            dirty = true;
        }
        if (dirty) sessionRepo.save(session);
    }

    private void updateEmotionIntensity(Session session, Session.HorsemenTurnEntry entry) {
        if (entry == null || entry.sender == null) return;
        double turnIntensity = avgNonNull(
                entry.criticism, entry.contempt, entry.defensiveness, entry.stonewalling);
        boolean isA = MessageSender.USER_A.name().equals(entry.sender);
        BigDecimal current = isA
                ? session.getUserAEmotionIntensity() : session.getUserBEmotionIntensity();
        Integer count = isA
                ? session.getUserAMessageCount() : session.getUserBMessageCount();
        int n = count == null ? 1 : Math.max(1, count);
        double prevAvg = current == null ? 0.0 : current.doubleValue();
        double nextAvg = ((prevAvg * (n - 1)) + turnIntensity) / n;
        BigDecimal value = BigDecimal.valueOf(Math.round(nextAvg * 100.0) / 100.0)
                .setScale(2, RoundingMode.HALF_UP);
        if (isA) session.setUserAEmotionIntensity(value);
        else session.setUserBEmotionIntensity(value);
    }

    private double avgNonNull(Double... values) {
        double sum = 0;
        int n = 0;
        for (Double v : values) {
            if (v != null) { sum += v; n++; }
        }
        return n == 0 ? 0 : sum / n;
    }

    private User loadUserSafely(String userId) {
        if (userId == null) return null;
        try {
            return userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    private void incrementUserMessageCount(Session session, MessageSender sender) {
        if (sender == MessageSender.USER_A) {
            int c = session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount();
            session.setUserAMessageCount(c + 1);
        } else if (sender == MessageSender.USER_B) {
            int c = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();
            session.setUserBMessageCount(c + 1);
        }
        sessionRepo.save(session);
    }

    private List<Message> getRecentMessagesForSolo(String sessionId, MessageSender sender, int limit) {
        var senders = sender == MessageSender.USER_A
                ? List.of(MessageSender.USER_A, MessageSender.MEDIATOR_TO_A)
                : List.of(MessageSender.USER_B, MessageSender.MEDIATOR_TO_B);
        var msgs = messageRepo.findBySessionIdAndSenderIn(sessionId, senders);
        return msgs.size() <= limit ? msgs : msgs.subList(msgs.size() - limit, msgs.size());
    }

    private List<Message> getRecentMessagesForDuo(String sessionId, int limit) {
        var msgs = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return msgs.size() <= limit ? msgs : msgs.subList(msgs.size() - limit, msgs.size());
    }

    private boolean checkAndTriggerFinalizationSuggestion(Session session) {
        if (session.getFinalizeSuggestedAt() != null) return false;

        int aCount = session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount();
        int bCount = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();
        boolean isDuo = stateMachine.isDuo(session.getStatus());

        boolean shouldSuggest = isDuo
                ? (aCount + bCount) >= ChatService.FINALIZE_SUGGEST_DUO_TOTAL_MIN
                    && aCount >= ChatService.FINALIZE_SUGGEST_DUO_PER_USER_MIN
                    && bCount >= ChatService.FINALIZE_SUGGEST_DUO_PER_USER_MIN
                : aCount >= ChatService.FINALIZE_SUGGEST_SOLO_MIN;

        if (shouldSuggest) {
            triggerFinalizationSuggestion(session, isDuo);
            return true;
        }
        return false;
    }

    private void triggerFinalizationSuggestion(Session session, boolean isDuo) {
        String suggestion = "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?";
        messageRepo.save(Message.builder()
                .sessionId(session.getId())
                .sender(MessageSender.MEDIATOR_TO_A)
                .content(suggestion)
                .charCount(suggestion.length())
                .isFinalizeSuggestion(true)
                .llmModel(ChatService.MODEL_HAIKU)
                .build());
        if (isDuo) {
            messageRepo.save(Message.builder()
                    .sessionId(session.getId())
                    .sender(MessageSender.MEDIATOR_TO_B)
                    .content(suggestion)
                    .charCount(suggestion.length())
                    .isFinalizeSuggestion(true)
                    .llmModel(ChatService.MODEL_HAIKU)
                    .build());
        }
        session.setFinalizeSuggestedAt(Instant.now());
        sessionRepo.save(session);
    }

    // 200자 초과 청크를 문장 경계에서 자동 분할
    private static final int MAX_CHUNK_LEN = 200;

    private static List<String> splitLongChunk(String chunk) {
        if (chunk.length() <= MAX_CHUNK_LEN) return List.of(chunk);
        List<String> parts = new ArrayList<>();
        String remaining = chunk;
        while (remaining.length() > MAX_CHUNK_LEN) {
            int splitAt = -1;
            for (int i = Math.min(MAX_CHUNK_LEN, remaining.length()) - 1; i >= MAX_CHUNK_LEN / 2; i--) {
                char c = remaining.charAt(i);
                if (c == '.' || c == '?' || c == '!' || c == '~' || c == '\n') {
                    splitAt = i + 1;
                    break;
                }
            }
            if (splitAt <= 0) splitAt = MAX_CHUNK_LEN;
            parts.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt).trim();
        }
        if (!remaining.isEmpty()) parts.add(remaining);
        return parts;
    }
}
