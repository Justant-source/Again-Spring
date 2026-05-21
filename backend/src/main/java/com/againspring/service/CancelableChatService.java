package com.againspring.service;

import com.againspring.common.exception.GuestLimitException;
import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.CancelableInvocation;
import com.againspring.llm.bridge.exception.InvocationCanceledException;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.crisis.CrisisDetector;
import com.againspring.service.prompt.ChatPromptAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final LLMProvider llmBridge;
    private final CrisisDetector crisisDetector;
    private final ChatPromptAssembler promptAssembler;
    private final SessionStateMachine stateMachine;
    private final ChatTurnProcessor chatTurnProcessor;
    private final TransactionTemplate transactionTemplate;
    private final com.againspring.config.UserPermissionsConfig permissions;

    // key = sessionId + ":" + sender.name() — A와 B 슬롯 독립 유지
    private final ConcurrentHashMap<String, CancelableInvocation> activeInvocations =
            new ConcurrentHashMap<>();

    public CancelableChatService(
            MessageRepository messageRepo,
            SessionRepository sessionRepo,
            UserRepository userRepo,
            LLMProvider llmBridge,
            CrisisDetector crisisDetector,
            ChatPromptAssembler promptAssembler,
            SessionStateMachine stateMachine,
            ChatTurnProcessor chatTurnProcessor,
            PlatformTransactionManager txManager,
            com.againspring.config.UserPermissionsConfig permissions) {
        this.messageRepo = messageRepo;
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.llmBridge = llmBridge;
        this.crisisDetector = crisisDetector;
        this.promptAssembler = promptAssembler;
        this.stateMachine = stateMachine;
        this.chatTurnProcessor = chatTurnProcessor;
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

        if (session.getFinalizeSuggestedAt() == null && ChatService.detectExitIntent(content)) {
            chatTurnProcessor.triggerFinalizationSuggestion(session, stateMachine.isDuo(session.getStatus()));
        }

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
                    ? getRecentMessagesForDuo(sessionId, 12)
                    : getRecentMessagesForSolo(sessionId, sender, 6);

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

        // 스트리밍 draft: 첫 partial 도착 시 INSERT, 이후 UPDATE.
        // DB 쓰기는 최소 500ms 간격으로 throttle.
        AtomicReference<Long> draftMsgIdRef = new AtomicReference<>(null);
        AtomicLong lastDbWriteAt = new AtomicLong(0);
        MessageSender mediatorSender = sender.mediatorCounterpart();

        inv.setPartialHandler(partial -> {
            long now = System.currentTimeMillis();
            if (now - lastDbWriteAt.get() < 500) return;  // throttle
            lastDbWriteAt.set(now);
            transactionTemplate.execute(txStatus -> {
                Long draftId = draftMsgIdRef.get();
                if (draftId == null) {
                    Message draft = messageRepo.save(Message.builder()
                            .sessionId(sessionId)
                            .sender(mediatorSender)
                            .content(partial)
                            .charCount(partial.length())
                            .status("streaming")
                            .llmModel(ChatService.MODEL_HAIKU)
                            .build());
                    draftMsgIdRef.set(draft.getId());
                } else {
                    messageRepo.updateStreamingContent(draftId, partial, partial.length());
                }
                return null;
            });
        });

        inv.getResultFuture().whenComplete((result, error) -> {
            if (activeInvocations.get(key) != inv) {
                log.debug("Invocation {} superseded for session {} sender {}", inv.getInvocationId(), sessionId, sender);
                cleanupDraft(draftMsgIdRef.get());
                return;
            }
            try {
                if (error == null) {
                    handleSuccessfulResponse(sessionId, sender, result, inv, draftMsgIdRef.get());
                } else if (!isCancellation(error)) {
                    log.error("LLM failed for session {}: {}", sessionId, error.getMessage());
                    cleanupDraft(draftMsgIdRef.get());
                    saveFallbackMessage(sessionId, mediatorSender);
                } else {
                    cleanupDraft(draftMsgIdRef.get());
                }
            } catch (Exception e) {
                log.error("Callback error for session {}", sessionId, e);
                cleanupDraft(draftMsgIdRef.get());
            } finally {
                activeInvocations.remove(key, inv);
            }
        });
    }

    public void cleanupSession(String sessionId) {
        cancelActiveInvocation(sessionId, MessageSender.USER_A, "session_cleanup");
        cancelActiveInvocation(sessionId, MessageSender.USER_B, "session_cleanup");
    }

    /**
     * Admin/FE용 — 특정 세션의 sender에 대해 진행 중인 LLM invocation이 있는지 확인.
     * 새로고침 후 TypingBubble 복원에 사용.
     */
    public boolean isInvocationActive(String sessionId, MessageSender sender) {
        return activeInvocations.containsKey(invocationKey(sessionId, sender));
    }

    /** 세션의 어떤 sender에든 진행 중 invocation이 있는지 확인 */
    public boolean isAnyInvocationActive(String sessionId) {
        return activeInvocations.containsKey(invocationKey(sessionId, MessageSender.USER_A))
                || activeInvocations.containsKey(invocationKey(sessionId, MessageSender.USER_B));
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
            String sessionId, MessageSender sender, String rawResponse,
            CancelableInvocation inv, Long draftMsgId) {
        transactionTemplate.execute(status -> {
            if (inv.isCanceled()) return null;

            Session session = sessionRepo.findById(sessionId).orElse(null);
            if (session == null || !stateMachine.isActive(session.getStatus())) return null;

            // 스트리밍 draft 삭제 후 최종 메시지 저장 (같은 트랜잭션)
            if (draftMsgId != null) {
                messageRepo.deleteById(draftMsgId);
            }
            chatTurnProcessor.process(session, sender, rawResponse, null);
            return null;
        });
    }

    private void cleanupDraft(Long draftMsgId) {
        if (draftMsgId == null) return;
        try {
            transactionTemplate.execute(s -> { messageRepo.deleteById(draftMsgId); return null; });
        } catch (Exception e) {
            log.warn("Failed to clean up streaming draft {}: {}", draftMsgId, e.getMessage());
        }
    }

    private void saveFallbackMessage(String sessionId, MessageSender mediatorSender) {
        transactionTemplate.execute(status -> {
            String fallback = "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?";
            // '-fallback' 접미사로 LLM 호출 실패율 모니터링에서 식별
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
}
