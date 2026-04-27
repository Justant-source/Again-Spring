package com.againspring.service;

import com.againspring.domain.Message;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.llm.bridge.ClaudeCodeBridge;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.context.UserStateAppender;
import com.againspring.service.crisis.CrisisDetector;
import com.againspring.service.parser.ChatTurnMetaParser;
import com.againspring.service.prompt.ChatPromptAssembler;
import com.againspring.service.report.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ClaudeCodeBridge llmBridge;
    private final CrisisDetector crisisDetector;
    private final ChatPromptAssembler promptAssembler;
    private final SessionStateMachine stateMachine;
    private final ReportGenerationService reportService;
    private final SessionRoleResolver roleResolver;
    private final ChatTurnMetaParser turnMetaParser;
    private final UserStateAppender userStateAppender; // Phase D PR-2

    public ChatService(MessageRepository messageRepo, SessionRepository sessionRepo,
                      UserRepository userRepo,
                      ClaudeCodeBridge llmBridge, CrisisDetector crisisDetector,
                      ChatPromptAssembler promptAssembler, SessionStateMachine stateMachine,
                      ReportGenerationService reportService, SessionRoleResolver roleResolver,
                      ChatTurnMetaParser turnMetaParser,
                      UserStateAppender userStateAppender) {
        this.messageRepo = messageRepo;
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.llmBridge = llmBridge;
        this.crisisDetector = crisisDetector;
        this.promptAssembler = promptAssembler;
        this.stateMachine = stateMachine;
        this.reportService = reportService;
        this.roleResolver = roleResolver;
        this.turnMetaParser = turnMetaParser;
        this.userStateAppender = userStateAppender;
    }

    public static final int MIN_MESSAGES_TO_FINALIZE = 3;
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

        int turnIndex = (session.getHorsemenHistory() == null ? 0 : session.getHorsemenHistory().size()) + 1;
        ChatTurnMetaParser.Result parsed = turnMetaParser.parse(
            mediatorResponseRaw, turnIndex, userSender.name());
        String mediatorResponse = parsed.mediatorMessage().isBlank()
            ? mediatorResponseRaw : parsed.mediatorMessage();
        appendPsychologyHistory(session, parsed);
        userStateAppender.append(session, parsed.userState()); // Phase D PR-2

        Message mediatorMsg = messageRepo.save(Message.builder()
            .sessionId(sessionId)
            .sender(mediatorSender)
            .content(mediatorResponse)
            .charCount(mediatorResponse.length())
            .llmModel(MODEL_HAIKU)
            .llmLatencyMs(latency)
            .build());

        // 4. 종료 권유 검토 (카운트 기반) + 명시적 종료 요청 시 재트리거
        boolean finalizeSuggested = checkAndTriggerFinalizationSuggestion(session);

        if (!finalizeSuggested && detectExitIntent(content)) {
            int myCount = userSender == MessageSender.USER_A
                ? (session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount())
                : (session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount());
            if (myCount >= MIN_MESSAGES_TO_FINALIZE) {
                triggerFinalizationSuggestion(session, isDuo);
                finalizeSuggested = true;
            }
        }

        return ChatTurnResult.success(userMsg, mediatorMsg, finalizeSuggested);
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

        if (session.getStatus() != SessionStatus.CHATTING_SOLO) {
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

        // 시스템 안내 메시지 — B에게 (B가 이제 방금 들어왔으니 인사 + 시작 유도)
        String bNotice = "함께 정리하러 와주셔서 고마워요. 천천히 마음을 들려주세요.\n"
                       + "상대분이 적으신 내용은 제가 따로 듣고 있어요. 두 분의 이야기는 서로 보이지 않아요.";
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

        // 자격 검증
        if (!isEligibleForFinalization(session, requestingUser, isDuo)) {
            throw new IllegalStateException("최소 3개 메시지 이후에 정리할 수 있어요");
        }

        if (!isDuo) {
            // Solo: 즉시 종료
            session.setStatus(SessionStatus.COMPLETED);
            session.setCompletedAt(Instant.now());
            sessionRepo.save(session);
            reportService.generateSoloReport(sessionId);
            return FinalizationResult.completedResult();
        }

        // Duo: 한쪽 동의 표기
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
            return FinalizationResult.completedResult();
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
        // 상태를 다시 CHATTING_DUO로 (Duo만 권유가 발생하므로)
        session.setStatus(SessionStatus.CHATTING_DUO);
        // 동의 플래그 초기화 (다음 권유 시 새로 받기)
        session.setFinalizeAgreedByA(false);
        session.setFinalizeAgreedByB(false);
        sessionRepo.save(session);
    }

    /**
     * 본인 채팅 메시지 폴링.
     * sender가 USER_A면 USER_A + MEDIATOR_TO_A 메시지만 반환.
     */
    public List<Message> getMyMessages(String sessionId, MessageSender currentUser, Instant since) {
        var senders = (currentUser == MessageSender.USER_A)
            ? List.of(MessageSender.USER_A, MessageSender.MEDIATOR_TO_A)
            : List.of(MessageSender.USER_B, MessageSender.MEDIATOR_TO_B);

        return messageRepo.findBySessionIdAndSenderIn(sessionId, senders).stream()
            .filter(m -> m.getCreatedAt().isAfter(since))
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
        if (dirty) {
            sessionRepo.save(session);
        }
    }

    private void updateEmotionIntensity(Session session, Session.HorsemenTurnEntry entry) {
        if (entry == null || entry.sender == null) return;
        double turnIntensity = avgNonNull(entry.criticism, entry.contempt,
            entry.defensiveness, entry.stonewalling);
        boolean isA = MessageSender.USER_A.name().equals(entry.sender);
        java.math.BigDecimal current = isA
            ? session.getUserAEmotionIntensity()
            : session.getUserBEmotionIntensity();
        Integer count = isA ? session.getUserAMessageCount() : session.getUserBMessageCount();
        int n = count == null ? 1 : Math.max(1, count);
        double prevAvg = current == null ? 0.0 : current.doubleValue();
        double nextAvg = ((prevAvg * (n - 1)) + turnIntensity) / n;
        java.math.BigDecimal value = java.math.BigDecimal.valueOf(Math.round(nextAvg * 100.0) / 100.0)
            .setScale(2, java.math.RoundingMode.HALF_UP);
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

    private boolean isEligibleForFinalization(Session session, MessageSender user, boolean isDuo) {
        int aCount = session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount();
        int bCount = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();

        if (!isDuo) {
            // Solo: 본인이 ≥3개 메시지
            return user == MessageSender.USER_A
                ? aCount >= MIN_MESSAGES_TO_FINALIZE
                : bCount >= MIN_MESSAGES_TO_FINALIZE;
        }
        // Duo: 양쪽 모두 ≥3개
        return aCount >= MIN_MESSAGES_TO_FINALIZE && bCount >= MIN_MESSAGES_TO_FINALIZE;
    }

    private boolean detectExitIntent(String content) {
        if (content == null) return false;
        String c = content.replace(" ", "");
        return c.contains("종료") || c.contains("끝내") || c.contains("그만하") ||
               c.contains("마무리") || c.contains("끝낼게") || c.contains("그만해") ||
               c.contains("대화끝") || c.contains("끝이야");
    }

    private boolean checkAndTriggerFinalizationSuggestion(Session session) {
        if (session.getFinalizeSuggestedAt() != null) return false;  // 이미 권유함

        int aCount = session.getUserAMessageCount() == null ? 0 : session.getUserAMessageCount();
        int bCount = session.getUserBMessageCount() == null ? 0 : session.getUserBMessageCount();

        boolean isDuo = stateMachine.isDuo(session.getStatus());
        boolean shouldSuggest;

        if (!isDuo) {
            // Solo: 5턴쯤이면 권유 가능
            shouldSuggest = aCount >= 5;
        } else {
            // Duo: 합쳐서 10턴 + 양쪽 ≥3턴
            shouldSuggest = (aCount + bCount) >= 10 && aCount >= 3 && bCount >= 3;
        }

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
            .llmModel(MODEL_HAIKU)
            .build());

        if (isDuo) {
            messageRepo.save(Message.builder()
                .sessionId(session.getId())
                .sender(MessageSender.MEDIATOR_TO_B)
                .content(suggestion)
                .charCount(suggestion.length())
                .isFinalizeSuggestion(true)
                .llmModel(MODEL_HAIKU)
                .build());
        }

        session.setFinalizeSuggestedAt(Instant.now());
        sessionRepo.save(session);
    }

    // ==== DTOs ====

    public record ChatTurnResult(
        boolean success,
        Message userMsg,
        Message mediatorMsg,
        boolean finalizeSuggested,
        Integer crisisLevel
    ) {
        public static ChatTurnResult success(Message u, Message m, boolean suggest) {
            return new ChatTurnResult(true, u, m, suggest, null);
        }
        public static ChatTurnResult crisisBlocked() {
            return new ChatTurnResult(false, null, null, false, 1);
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
