package com.againspring.service;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.llm.LLMProvider;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.context.WelcomeMessageGenerator;
import com.againspring.service.context.WelcomeQuestionResolver;
import com.againspring.service.crisis.CrisisDetector;
import com.againspring.service.prompt.ChatPromptAssembler;
import com.againspring.service.report.ReportGenerationService;
import com.againspring.service.event.PartnerJoinedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ChatService (V1.5 카톡식 채팅)
 * 메인 비즈니스 로직: 메시지 송수신, AI 응답, 종료 권유, 상태 전이
 */
@Slf4j
@Service
public class ChatService {

    private final MessageRepository messageRepo;
    private final SessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final LLMProvider llmBridge;
    private final CrisisDetector crisisDetector;
    private final ChatPromptAssembler promptAssembler;
    private final SessionStateMachine stateMachine;
    private final ReportGenerationService reportService;
    private final ChatTurnProcessor chatTurnProcessor;
    private final WelcomeQuestionResolver welcomeQuestionResolver;
    private final WelcomeMessageGenerator welcomeMessageGenerator;

    // Lazy-injected to avoid circular dependency with CancelableChatService
    @Lazy
    private CancelableChatService cancelableChatService;

    public ChatService(MessageRepository messageRepo, SessionRepository sessionRepo,
                      UserRepository userRepo,
                      @Qualifier("chatLlmProvider") LLMProvider llmBridge, CrisisDetector crisisDetector,
                      ChatPromptAssembler promptAssembler, SessionStateMachine stateMachine,
                      ReportGenerationService reportService,
                      ChatTurnProcessor chatTurnProcessor,
                      WelcomeQuestionResolver welcomeQuestionResolver,
                      WelcomeMessageGenerator welcomeMessageGenerator,
                      @Lazy CancelableChatService cancelableChatService) {
        this.messageRepo = messageRepo;
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.llmBridge = llmBridge;
        this.crisisDetector = crisisDetector;
        this.promptAssembler = promptAssembler;
        this.stateMachine = stateMachine;
        this.reportService = reportService;
        this.chatTurnProcessor = chatTurnProcessor;
        this.welcomeQuestionResolver = welcomeQuestionResolver;
        this.welcomeMessageGenerator = welcomeMessageGenerator;
        this.cancelableChatService = cancelableChatService;
    }

    @Value("${app.session.min-messages-to-finalize:5}")
    private int MIN_MESSAGES_TO_FINALIZE;
    public static final int FINALIZE_SUGGEST_SOLO_MIN = 10;
    public static final int FINALIZE_SUGGEST_DUO_TOTAL_MIN = 16;
    public static final int FINALIZE_SUGGEST_DUO_PER_USER_MIN = 5;
    public static final String MODEL_HAIKU = "claude-haiku-4-5-20251001";
    public static final String MODEL_SONNET = "claude-sonnet-4-20250514";

    /**
     * 사용자가 메시지 전송.
     * 1) 위기 감지
     * 2) 메시지 저장
     * 3) AI 응답 생성 (Solo 또는 Duo 컨텍스트)
     * 4) 종료 권유 트리거 검토
     */
    @Transactional
    public ChatTurnResult sendUserMessage(String sessionId, MessageSender userSender, String content) {
        Session session = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!stateMachine.isActive(session.getStatus())) {
            throw new IllegalStateException("Session is not active: " + session.getStatus());
        }

        // 1. 위기 감지 (LLM 호출 전 비용 절감)
        var crisis = crisisDetector.detect(content);
        if (crisis.level() == 1) {
            log.warn("Crisis level 1 detected in session {}", sessionId);
            return ChatTurnResult.crisisBlocked();
        }

        // 2. 사용자 메시지 저장 + 카운트 증가
        Message userMsg = messageRepo.save(Message.builder()
            .sessionId(sessionId)
            .sender(userSender)
            .content(content)
            .charCount(content.length())
            .crisisLevel(crisis.level() == 2 ? 2 : null)
            .build());
        incrementUserMessageCount(session, userSender);

        // 3. AI 응답 생성
        boolean isDuo = stateMachine.isDuo(session.getStatus());
        MessageSender mediatorSender = userSender.mediatorCounterpart();

        List<Message> recentMessages;
        if (isDuo) {
            recentMessages = getRecentMessagesForDuo(sessionId, 20);
        } else {
            recentMessages = getRecentMessagesForSolo(sessionId, userSender, 10);
        }

        String prompt;
        try {
            if (isDuo) {
                User userA = loadUserSafely(session.getUserAId());
                User userB = loadUserSafely(session.getUserBId());
                prompt = promptAssembler.assembleDuoTurn(session, userA, userB, userSender, content, recentMessages);
            } else {
                String soloUserId = (userSender == MessageSender.USER_A)
                    ? session.getUserAId() : session.getUserBId();
                User soloUser = loadUserSafely(soloUserId);
                prompt = promptAssembler.assembleSoloTurn(session, soloUser, content, recentMessages);
            }
        } catch (Exception e) {
            log.error("Failed to assemble prompt for session {}", sessionId, e);
            prompt = ""; // Fallback empty prompt
        }

        long start = System.currentTimeMillis();
        String mediatorResponseRaw;
        try {
            mediatorResponseRaw = llmBridge.invoke(prompt, MODEL_HAIKU);
            if (mediatorResponseRaw == null || mediatorResponseRaw.isBlank()) {
                throw new IllegalStateException("Empty LLM response");
            }
        } catch (Exception e) {
            log.error("LLM call failed for session {}: {}", sessionId, e.getMessage(), e);
            mediatorResponseRaw = "잠깐 정리할 시간이 필요해요. 다시 들려주실 수 있을까요?";
        }
        long latency = System.currentTimeMillis() - start;

        ChatTurnProcessor.TurnProcessResult turnResult =
                chatTurnProcessor.process(session, userSender, mediatorResponseRaw, latency);

        // 4. 종료 권유 재트리거 — 사용자가 명시적 종료 의사를 표현했을 때 (count 기반 트리거와 별개)
        boolean finalizeSuggested = turnResult.finalizeSuggested();
        if (!finalizeSuggested && detectExitIntent(content)) {
            int myCount = userSender == MessageSender.USER_A
                ? (session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount())
                : (session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount());
            if (myCount >= MIN_MESSAGES_TO_FINALIZE) {
                chatTurnProcessor.triggerFinalizationSuggestion(session, isDuo);
                finalizeSuggested = true;
            }
        }

        return ChatTurnResult.success(userMsg, turnResult.mediatorMessages(), finalizeSuggested);
    }

    /**
     * joinSession 트랜잭션 커밋 후 이벤트 기반 호출.
     * join 트랜잭션이 완전히 커밋된 다음 LLM 호출이 시작되어야
     * B의 첫 메시지 도착 시 SessionRoleResolver가 userBId를 정상 인식함.
     * 실패 시 폴백 메시지를 저장하여 B가 빈 채팅방에 진입하지 않도록 함.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPartnerJoinedEvent(PartnerJoinedEvent event) {
        try {
            onPartnerJoined(event.getSessionId(), event.getUserBId());
        } catch (Exception e) {
            log.error("onPartnerJoined failed for session {}: {}", event.getSessionId(), e.getMessage(), e);
            saveFallbackPartnerJoinMessages(event.getSessionId());
        }
    }

    /**
     * Solo→Duo 전이. 상대가 invite 토큰으로 join한 순간 호출됨.
     * - 상태 전이
     * - 양쪽 채팅에 "상대가 합류했어요" 시스템 메시지 삽입
     * - 단, 양쪽의 본문은 절대 노출되지 않음 (격리 원칙)
     */
    @Transactional
    public void onPartnerJoined(String sessionId, String userBId) {
        Session session = sessionRepo.findById(sessionId).orElseThrow();

        boolean canTransitionToDuo = session.getStatus() == SessionStatus.CHATTING_SOLO
                || (session.getStatus() == SessionStatus.COMPLETED && Boolean.TRUE.equals(session.getSoloMode()));
        if (!canTransitionToDuo) {
            log.warn("Cannot transition to DUO from {}", session.getStatus());
            return;
        }

        session.setStatus(SessionStatus.CHATTING_DUO);
        session.setUserBId(userBId);
        session.setPartnerJoinedAt(Instant.now());
        sessionRepo.save(session);

        // A에게 Solo 대화 맥락을 반영한 전환 메시지 (AI 생성, 실패 시 fallback)
        String aNotice = generatePartnerJoinedNoticeForA(sessionId);
        messageRepo.save(Message.builder()
            .sessionId(sessionId)
            .sender(MessageSender.MEDIATOR_TO_A)
            .content(aNotice)
            .charCount(aNotice.length())
            .isPartnerJoinNotice(true)
            .llmModel(MODEL_HAIKU)
            .build());

        // Phase D PR-5: B 진입 환영 + 첫 질문 — 동적 생성
        Session.PendingQuestion welcomeQ = welcomeQuestionResolver.resolveOrCreate(session);
        String bNotice = welcomeMessageGenerator.generate(session, welcomeQ);
        welcomeQ.asked = true;
        welcomeQ.askedTurn = 0;
        sessionRepo.save(session);

        messageRepo.save(Message.builder()
            .sessionId(sessionId)
            .sender(MessageSender.MEDIATOR_TO_B)
            .content(bNotice)
            .charCount(bNotice.length())
            .isPartnerJoinNotice(true)
            .llmModel(MODEL_HAIKU)
            .build());

        log.info("Session {} transitioned SOLO → DUO", sessionId);
    }

    private String generatePartnerJoinedNoticeForA(String sessionId) {
        final String fallback = "상대분이 함께 정리하기 시작하셨어요. 제가 두 분의 마음을 같이 살펴드릴게요.";
        try {
            List<Message> all = messageRepo.findBySessionIdAndSenderIn(
                    sessionId,
                    List.of(MessageSender.USER_A, MessageSender.MEDIATOR_TO_A));
            List<Message> soloMessages = all.size() > 10 ? all.subList(all.size() - 10, all.size()) : all;
            if (soloMessages.isEmpty()) return fallback;
            String prompt = promptAssembler.assemblePartnerJoinedSummaryPrompt(soloMessages);
            String result = llmBridge.invoke(prompt, MODEL_HAIKU);
            return (result != null && !result.isBlank()) ? result.strip() : fallback;
        } catch (Exception e) {
            log.warn("Partner-joined summary LLM failed for session {}: {}", sessionId, e.getMessage());
            return fallback;
        }
    }

    /**
     * 명시적 정리 요청.
     * Solo: 본인 ≥3턴이면 즉시 COMPLETED + 리포트
     * Duo: 본인 ≥3턴 + 양쪽 ≥3턴이면 AWAITING_FINALIZATION → 상대 동의 시 COMPLETED
     */
    @Transactional
    public FinalizationResult requestFinalization(String sessionId, MessageSender requestingUser) {
        Session session = sessionRepo.findById(sessionId).orElseThrow();
        boolean isDuo = stateMachine.isDuo(session.getStatus());

        // 자격 검증 — 요청자 본인의 메시지 수만 확인 (상대방 미참여여도 정리 가능)
        int aCount = session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount();
        int bCount = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();
        int myCount = requestingUser == MessageSender.USER_A ? aCount : bCount;
        int partnerCount = requestingUser == MessageSender.USER_A ? bCount : aCount;

        if (myCount < MIN_MESSAGES_TO_FINALIZE) {
            throw new IllegalStateException(
                "아직 대화가 충분하지 않아요. " + MIN_MESSAGES_TO_FINALIZE + "개 이상 이야기한 뒤 정리할 수 있어요.");
        }

        if (!isDuo) {
            // Solo: 즉시 종료
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());
            sessionRepo.save(session);
            reportService.generateSoloReport(sessionId);

            // Bug E: Cleanup active invocations when session completes
            if (cancelableChatService != null) {
                cancelableChatService.cleanupSession(sessionId);
            }

            return FinalizationResult.completedResult();
        }

        // Duo: 이미 동의 표기가 있으면 중복 알림 방지
        boolean wasAlreadyAgreed = requestingUser == MessageSender.USER_A
            ? Boolean.TRUE.equals(session.getFinalizeAgreedByA())
            : Boolean.TRUE.equals(session.getFinalizeAgreedByB());

        // 한쪽 동의 표기
        if (requestingUser == MessageSender.USER_A) session.setFinalizeAgreedByA(true);
        else session.setFinalizeAgreedByB(true);

        session.setStatus(SessionStatus.AWAITING_FINALIZATION);
        sessionRepo.save(session);

        // 양쪽 모두 동의 시 즉시 종료
        if (Boolean.TRUE.equals(session.getFinalizeAgreedByA())
            && Boolean.TRUE.equals(session.getFinalizeAgreedByB())) {
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());
            sessionRepo.save(session);
            reportService.generateDuoReport(sessionId);

            // Bug E: Cleanup active invocations when session completes
            if (cancelableChatService != null) {
                cancelableChatService.cleanupSession(sessionId);
            }

            return FinalizationResult.completedResult();
        }

        // 처음 정리 요청인 경우에만 상대방에게 안내 메시지 전송
        // 상대방 측에 이미 finalizeSuggestion 카드(AI 자동 제안 또는 이전 알림)가 있으면 중복 생성 방지
        MessageSender partnerSender = requestingUser == MessageSender.USER_A
            ? MessageSender.MEDIATOR_TO_B
            : MessageSender.MEDIATOR_TO_A;
        boolean partnerAlreadyHasSuggestion = messageRepo
            .existsBySessionIdAndSenderAndIsFinalizeSuggestionTrue(sessionId, partnerSender);

        if (!wasAlreadyAgreed && !partnerAlreadyHasSuggestion) {
            String notice = partnerCount == 0
                ? "상대방이 대화를 정리했어요. 아직 전하고 싶은 말이 있다면 더 이야기해도 괜찮아요. 없다면 함께 마무리해요."
                : "상대방이 대화를 정리했어요. 더 전할 말이 있거나 상대방의 감정을 받아줄 의도가 아니라면, 함께 대화를 마무리해요.";
            messageRepo.save(Message.builder()
                .sessionId(sessionId)
                .sender(partnerSender)
                .content(notice)
                .charCount(notice.length())
                .isFinalizeSuggestion(true)
                .build());
        }

        // 정리하기 요청 시 본인 측 finalizeSuggestion 카드 dismiss (재진입 시 카드 안 보임, 안내는 status로)
        MessageSender myMediatorSender = (requestingUser == MessageSender.USER_A)
            ? MessageSender.MEDIATOR_TO_A : MessageSender.MEDIATOR_TO_B;
        var mySuggestions = messageRepo
            .findBySessionIdAndSenderAndIsFinalizeSuggestionTrueAndDismissedAtIsNull(sessionId, myMediatorSender);
        if (!mySuggestions.isEmpty()) {
            Instant now = Instant.now();
            mySuggestions.forEach(m -> m.setDismissedAt(now));
            messageRepo.saveAll(mySuggestions);
        }

        return FinalizationResult.awaitingPartnerResult();
    }

    @Transactional
    public FinalizationResult agreeToFinalize(String sessionId, MessageSender agreeingUser) {
        return requestFinalization(sessionId, agreeingUser);
    }

    @Transactional
    public void declineFinalize(String sessionId, MessageSender decliningUser) {
        Session session = sessionRepo.findById(sessionId).orElseThrow();
        // 원래 모드로 복귀 — Solo면 CHATTING_SOLO, 상대가 합류한 Duo면 CHATTING_DUO.
        // 이전 버그: 무조건 CHATTING_DUO로 바꿔 Solo 세션이 강제로 Duo UI(SwipeContainer)로 전환되어
        //          "상대가 초대된 것처럼" 보였음.
        if (session.getUserBId() != null) {
            session.setStatus(SessionStatus.CHATTING_DUO);
        } else {
            session.setStatus(SessionStatus.CHATTING_SOLO);
        }
        // 동의 플래그 초기화 (다음 권유 시 새로 받기)
        session.setFinalizeAgreedByA(false);
        session.setFinalizeAgreedByB(false);
        sessionRepo.save(session);

        // finalizeSuggestion 메시지를 본인 측에서 dismiss 처리 (DB 영속 → 재진입 시 카드 안 보임)
        MessageSender senderToDismiss = (decliningUser == MessageSender.USER_A)
            ? MessageSender.MEDIATOR_TO_A : MessageSender.MEDIATOR_TO_B;
        var pending = messageRepo
            .findBySessionIdAndSenderAndIsFinalizeSuggestionTrueAndDismissedAtIsNull(sessionId, senderToDismiss);
        if (!pending.isEmpty()) {
            Instant now = Instant.now();
            pending.forEach(m -> m.setDismissedAt(now));
            messageRepo.saveAll(pending);
        }
    }

    /**
     * 본인 채팅 메시지 폴링.
     * sender가 USER_A면 USER_A + MEDIATOR_TO_A 메시지만 반환.
     */
    public List<Message> getMyMessages(String sessionId, MessageSender currentUser, Instant since) {
        var senders = (currentUser == MessageSender.USER_A)
            ? List.of(MessageSender.USER_A, MessageSender.MEDIATOR_TO_A)
            : List.of(MessageSender.USER_B, MessageSender.MEDIATOR_TO_B);

        Instant safeSince = since.minusMillis(1);
        return messageRepo.findBySessionIdAndSenderIn(sessionId, senders).stream()
            .filter(m -> m.getCreatedAt().isAfter(safeSince))
            .filter(m -> m.getDismissedAt() == null)
            .toList();
    }

    /**
     * 상대 패널용 메타데이터 (content 절대 포함 X — 격리 원칙)
     */
    public List<MessageMetadata> getPartnerMessagesMetadata(String sessionId, MessageSender currentUser) {
        var partnerSenders = (currentUser == MessageSender.USER_A)
            ? List.of(MessageSender.USER_B, MessageSender.MEDIATOR_TO_B)
            : List.of(MessageSender.USER_A, MessageSender.MEDIATOR_TO_A);

        return messageRepo.findBySessionIdAndSenderIn(sessionId, partnerSenders).stream()
            .map(m -> new MessageMetadata(m.getId(), m.getSender(), m.getCharCount(), m.getCreatedAt()))
            .toList();
    }

    public PartnerStatus getPartnerStatus(String sessionId, MessageSender currentUser) {
        Session session = sessionRepo.findById(sessionId).orElseThrow();
        boolean isDuo = stateMachine.isDuo(session.getStatus());

        if (!isDuo) {
            // Solo 단계 — 상대가 아직 안 들어옴
            boolean inviteSent = session.getInviteToken() != null && session.getUserBId() == null;
            return new PartnerStatus(false, false, inviteSent, 0, null);
        }

        // Duo 단계 — 상대 합류함
        int partnerCount = (currentUser == MessageSender.USER_A)
            ? (session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount())
            : (session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount());

        var partnerLastSender = (currentUser == MessageSender.USER_A) ? MessageSender.USER_B : MessageSender.USER_A;
        Instant lastActivity = messageRepo.findBySessionIdAndSenderIn(sessionId, List.of(partnerLastSender)).stream()
            .map(Message::getCreatedAt)
            .max(java.util.Comparator.naturalOrder())
            .orElse(null);

        boolean isActive = lastActivity != null && lastActivity.isAfter(Instant.now().minusSeconds(60));
        return new PartnerStatus(true, isActive, false, partnerCount, lastActivity);
    }

    // ==== helpers ====

    private User loadUserSafely(String userId) {
        if (userId == null) return null;
        try {
            return userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load user {} for prompt enrichment: {}", userId, e.getMessage());
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

    private List<Message> getRecentMessagesForSolo(String sessionId, MessageSender userSender, int limit) {
        // Solo: 본인+본인의 중재자 응답만
        var senders = (userSender == MessageSender.USER_A)
            ? List.of(MessageSender.USER_A, MessageSender.MEDIATOR_TO_A)
            : List.of(MessageSender.USER_B, MessageSender.MEDIATOR_TO_B);
        var msgs = messageRepo.findBySessionIdAndSenderIn(sessionId, senders);
        return msgs.size() <= limit ? msgs : msgs.subList(msgs.size() - limit, msgs.size());
    }

    private List<Message> getRecentMessagesForDuo(String sessionId, int limit) {
        // Duo: 양쪽 모두의 메시지를 시간순으로 (AI는 양쪽 컨텍스트를 봄)
        var msgs = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        return msgs.size() <= limit ? msgs : msgs.subList(msgs.size() - limit, msgs.size());
    }

    static boolean detectExitIntent(String content) {
        if (content == null) return false;
        String c = content.replace(" ", "");
        return c.contains("종료") || c.contains("끝내") || c.contains("그만하") ||
               c.contains("마무리") || c.contains("끝낼게") || c.contains("그만해") ||
               c.contains("대화끝") || c.contains("끝이야") || c.contains("이만하면됐");
    }

    /** finalize 자동 트리거 게이트 — 사용자가 실제 해소(RESOLVING) 단계에 도달했는지 검사 */
    static boolean hasReachedResolvingState(Session session) {
        List<Session.UserStateEntry> hist = session.getUserStateHistory();
        if (hist == null || hist.isEmpty()) return false;
        for (Session.UserStateEntry e : hist) {
            if (e != null && e.state == Session.UserState.RESOLVING) return true;
        }
        return false;
    }

    /**
     * onPartnerJoined 실패 시 폴백: 양쪽 채팅에 기본 환영 메시지 저장.
     * LLM 호출 없이 순수 텍스트만 저장. 이 메서드에서 예외 발생해도 로그만 기록.
     */
    private void saveFallbackPartnerJoinMessages(String sessionId) {
        try {
            String aNotice = "상대분이 함께하러 오셨어요. 두 분의 이야기를 함께 들어볼게요.";
            messageRepo.save(Message.builder()
                .sessionId(sessionId)
                .sender(MessageSender.MEDIATOR_TO_A)
                .content(aNotice)
                .charCount(aNotice.length())
                .isPartnerJoinNotice(true)
                .build());

            String bNotice = "함께 이야기를 나눠볼까요? 편하게 마음을 들려주세요.";
            messageRepo.save(Message.builder()
                .sessionId(sessionId)
                .sender(MessageSender.MEDIATOR_TO_B)
                .content(bNotice)
                .charCount(bNotice.length())
                .isPartnerJoinNotice(true)
                .build());

            log.info("Fallback partner join messages saved for session {}", sessionId);
        } catch (Exception ex) {
            log.error("Fallback partner join messages also failed for session {}: {}",
                    sessionId, ex.getMessage());
        }
    }

    // ==== DTOs ====

    public record ChatTurnResult(
        boolean success,
        Message userMsg,
        List<Message> mediatorMessages,
        boolean finalizeSuggested,
        Integer crisisLevel
    ) {
        public static ChatTurnResult success(Message u, List<Message> msgs, boolean suggest) {
            return new ChatTurnResult(true, u, msgs, suggest, null);
        }
        public static ChatTurnResult crisisBlocked() {
            return new ChatTurnResult(false, null, null, false, 1);
        }
        public Message mediatorMsg() {
            return (mediatorMessages != null && !mediatorMessages.isEmpty()) ? mediatorMessages.get(0) : null;
        }
    }

    public record FinalizationResult(boolean completed, boolean awaitingPartner) {
        public static FinalizationResult completedResult() { return new FinalizationResult(true, false); }
        public static FinalizationResult awaitingPartnerResult() { return new FinalizationResult(false, true); }
    }

    public record MessageMetadata(Long id, MessageSender sender, Integer charCount, Instant createdAt) {}

    public record PartnerStatus(
        boolean joined,             // Solo→Duo 전이됐는지
        boolean isActive,           // 최근 60초 안에 활동
        boolean inviteSent,         // 초대 보냈지만 미합류
        int messageCount,
        Instant lastActivityAt
    ) {}

}
