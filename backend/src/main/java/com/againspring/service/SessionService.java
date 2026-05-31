package com.againspring.service;

import com.againspring.api.dto.request.CreateSessionRequest;
import com.againspring.api.dto.request.JoinSessionRequest;
import com.againspring.api.dto.response.CreateSessionResponse;
import com.againspring.api.dto.response.SessionListItemResponse;
import com.againspring.api.dto.response.SessionResponse;
import com.againspring.api.dto.response.SessionStatusResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.common.exception.DailyLimitExceededException;
import com.againspring.domain.enums.RelationType;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.Message;
import com.againspring.domain.enums.MessageSender;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.ScanResult;
import com.againspring.service.event.PartnerJoinedEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session service for CRUD operations.
 * Includes state machine validation and keyword guard integration.
 */
@Service
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final KeywordGuard keywordGuard;
    private final SessionStateMachine stateMachine;
    private final ChatService chatService;
    private final ApplicationEventPublisher eventPublisher;
    private final GuestSessionRateLimiter guestSessionRateLimiter;
    private final MessageRepository messageRepository;
    private final com.againspring.service.context.FirstMessageTemplateService firstMessageTemplateService;

    public SessionService(SessionRepository sessionRepository,
                          UserRepository userRepository,
                          KeywordGuard keywordGuard,
                          SessionStateMachine stateMachine,
                          @Lazy ChatService chatService,
                          ApplicationEventPublisher eventPublisher,
                          GuestSessionRateLimiter guestSessionRateLimiter,
                          MessageRepository messageRepository,
                          com.againspring.service.context.FirstMessageTemplateService firstMessageTemplateService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.keywordGuard = keywordGuard;
        this.stateMachine = stateMachine;
        this.chatService = chatService;
        this.eventPublisher = eventPublisher;
        this.guestSessionRateLimiter = guestSessionRateLimiter;
        this.messageRepository = messageRepository;
        this.firstMessageTemplateService = firstMessageTemplateService;
    }

    private static final long INVITE_TOKEN_TTL_MS = 86400000; // 24 hours
    private static final long CONTENT_EXPIRY_TTL_MS = 2592000000L; // 30 days

    @Value("${app.session.max-active:3}")
    private int MAX_ACTIVE_SESSIONS_PER_USER;

    /**
     * Create a new session (A initiates).
     *
     * @param createdByUserId the creator user ID
     * @param request create request
     * @return create session response with invite token
     * @throws BusinessException if description contains crisis keywords
     */
    public CreateSessionResponse createSession(String createdByUserId, CreateSessionRequest request, String clientIp) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(createdByUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다"));
        // 온보딩(성격검사)은 선택 사항 — 가입 직후 강제하지 않음.
        // communicationStyle이 없으면 UserProfileFragment가 프롬프트에서 자동 생략되며,
        // 중재는 Phase D 동적 컨텍스트(대화 기반)로 정상 동작한다.
        // 스타일/MBTI는 프로필에서 옵션으로 설정 가능(추후 중재자 파라미터 확장점).

        // 게스트 제한: IP당 24시간 3세션
        if (creator.isGuest() && clientIp != null) {
            if (!guestSessionRateLimiter.tryConsumeGuestSession(clientIp)) {
                throw new BusinessException("GUEST_SESSION_LIMIT",
                        "오늘 이용 가능한 체험 세션 횟수를 초과했습니다. 내일 다시 시도하거나 회원가입해 주세요.", 429);
            }
        }

        // 회원 일일 세션 한도: KST 자정 기준 5세션
        if (!creator.isGuest()) {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            LocalDate today = LocalDate.now(kst);
            Instant startOfDay = today.atStartOfDay(kst).toInstant();
            Instant endOfDay = today.plusDays(1).atStartOfDay(kst).toInstant();
            int todayCount = sessionRepository.countByCreatedByUserIdAndCreatedAtBetween(
                    createdByUserId, startOfDay, endOfDay);
            if (todayCount >= 5) {
                throw new DailyLimitExceededException();
            }
        }

        // 동시 진행 중인 세션 한도 체크
        List<SessionStatus> activeStatuses = List.of(SessionStatus.CHATTING_SOLO, SessionStatus.CHATTING_DUO);
        List<Session> createdSessions = sessionRepository.findByCreatedByUserIdAndStatusIn(createdByUserId, activeStatuses);
        List<Session> joinedSessions = sessionRepository.findByInviteeUserIdAndStatusIn(createdByUserId, activeStatuses);
        int activeSessions = createdSessions.size() + joinedSessions.size();
        if (activeSessions >= MAX_ACTIVE_SESSIONS_PER_USER) {
            throw new BusinessException("SESSION_LIMIT_EXCEEDED",
                    "동시에 진행 중인 대화가 너무 많아요. 기존 대화를 먼저 마무리해 주세요.", 429);
        }

        // 대분류(relationType)는 사용자가 선택 — 필수 검증 (V47~: 중·소분류만 제거)
        try {
            RelationType.fromValue(request.getRelationType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_INPUT", "올바르지 않은 관계 유형이에요.");
        }

        // Scan description for keywords (crisis detection)
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            ScanResult scanResult = keywordGuard.scanUserInput(request.getDescription(), createdByUserId);
            if (scanResult.isCrisis()) {
                log.warn("Crisis detected in session description: userId={}, level={}",
                        createdByUserId, scanResult.getMaxLevel());
                throw new BusinessException(
                        "CRISIS_DETECTED",
                        "중요한 안내가 필요한 상황이 감지되었어요",
                        422);
            }
        }

        // Generate invite token
        String inviteToken = "inv_" + UUID.randomUUID().toString().substring(0, 12);
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(INVITE_TOKEN_TTL_MS);

        // V47~: 카테고리 선택 제거. majorId는 기존 API 호환용으로 수신할 수 있으나
        // 중·소분류는 무시. relationType 및 koreanTag는 SessionMetaInferenceService가 추론.
        Session.Category category = null;
        if (request.getCategory() != null && request.getCategory().getMajorId() != null) {
            category = new Session.Category();
            category.majorId = request.getCategory().getMajorId();
            category.customText = request.getCategory().getCustomText();
        }

        // V1.5: 모든 세션은 CHATTING_SOLO로 시작 (초대 여부 무관)
        // 상대 join은 별도 메서드로 처리 (generateInviteForExistingSession)
        // User의 mediator 기본값 프리필 (X축은 V22부터 존재, Y축은 V47 신규)
        int styleX = request.getMediatorStyleX() != null ? request.getMediatorStyleX()
                : (creator.getMediatorDefaultX() != null ? creator.getMediatorDefaultX() : 50);
        int styleY = request.getMediatorStyleY() != null ? request.getMediatorStyleY()
                : (creator.getMediatorDefaultY() != null ? creator.getMediatorDefaultY() : 50);

        RelationType relationType = RelationType.fromValue(request.getRelationType());

        String sessionId = "ses_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Session session = Session.builder()
                .id(sessionId)
                .createdByUserId(createdByUserId)
                .relationType(relationType)
                .category(category)
                .status(SessionStatus.CHATTING_SOLO)  // V1.5: 항상 SOLO로 시작
                .soloMode(true)                         // V1.5: default true
                .userAMessageCount(0)
                .userBMessageCount(0)
                .finalizeAgreedByA(false)
                .finalizeAgreedByB(false)
                .mediatorStyleX(styleX)
                .mediatorStyleY(styleY)
                .crisisDetections(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .contentExpiresAt(now.plusMillis(CONTENT_EXPIRY_TTL_MS))
                .build();

        // V1.5: 초대 토큰은 필요시에 generateInviteForExistingSession으로 생성
        // (createSession 단계에서는 생성하지 않음)

        Session saved = sessionRepository.save(session);
        log.info("Session created: id={}, token={}, creator={}", saved.getId(), inviteToken, createdByUserId);

        // V47: 대분류별 predefined 첫마디 비동기 저장 (세션 생성 응답 블로킹 제거)
        firstMessageTemplateService.generateAndSaveAsync(saved);

        String inviteUrl = "https://againspring.app/join/" + inviteToken;

        return CreateSessionResponse.builder()
                .id(saved.getId())
                .inviteToken(inviteToken)
                .inviteUrl(inviteUrl)
                .status(saved.getStatus().getValue())
                .currentTurn(saved.getCurrentTurn())
                .createdAt(saved.getCreatedAt())
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Get session by ID with access control.
     *
     * @param sessionId the session ID
     * @param userId the requesting user ID
     * @return session response
     * @throws BusinessException if session not found or access denied
     */
    @Transactional(readOnly = true)
    public SessionResponse getSession(String sessionId, String userId) {
        return getSession(sessionId, userId, false);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(String sessionId, String userId, boolean isAdmin) {
        Session session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "세션을 찾을 수 없어요."));

        // ADMIN can view testRun simulation sessions (createdByUserId = "marketing_system")
        if (isAdmin && Boolean.TRUE.equals(session.getTestRun())) {
            return mapToSessionResponse(session, session.getCreatedByUserId());
        }

        // Access control: creator or invitee only
        if (!session.getCreatedByUserId().equals(userId)
                && !((session.getInviteeUserId() != null && session.getInviteeUserId().equals(userId)))) {
            throw new BusinessException("SESSION_FORBIDDEN", "이 세션에 접근할 권한이 없어요.");
        }

        return mapToSessionResponse(session, userId);
    }

    /**
     * Get user's sessions (created or joined).
     *
     * @param userId the user ID
     * @return list of session list items, sorted by creation date descending
     */
    @Transactional(readOnly = true)
    public List<SessionListItemResponse> getUserSessions(String userId) {
        List<Session> sessions = sessionRepository
                .findByCreatedByUserIdOrInviteeUserIdOrderByCreatedAtDesc(userId, userId);

        return sessions.stream()
                .map(this::mapToSessionListItem)
                .collect(Collectors.toList());
    }

    /**
     * Join a session via invite token (B joins or guest joins).
     * V1.5: Solo→Duo 전이 (ChatService에 위임)
     *
     * @param inviteToken the invite token
     * @param request join request
     * @param userId the joining user ID (may be null for guests)
     * @return session response
     * @throws BusinessException if token invalid/expired or session already joined
     */
    @Transactional
    public SessionResponse joinSession(String inviteToken, JoinSessionRequest request, Optional<String> userId) {
        Session session = sessionRepository
                .findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException("INVITE_TOKEN_INVALID", "유효하지 않은 초대 링크예요."));

        // Check expiry
        if (session.getInviteExpiresAt() != null && Instant.now().isAfter(session.getInviteExpiresAt())) {
            throw new BusinessException("INVITE_TOKEN_EXPIRED", "초대 링크가 만료되었어요.");
        }

        // V1.5: CHATTING_SOLO 또는 COMPLETED(soloMode) 상태에서 join 가능
        boolean isSoloCompleted = session.getStatus().equals(SessionStatus.COMPLETED)
                && Boolean.TRUE.equals(session.getSoloMode());
        if (!session.getStatus().equals(SessionStatus.CHATTING_SOLO) && !isSoloCompleted) {
            throw new BusinessException(
                    "SESSION_INVALID_STATE",
                    "This session is no longer available for joining");
        }

        // 본인이 자기 세션에 join 불가
        if (userId.isPresent() && userId.get().equals(session.getCreatedByUserId())) {
            throw new BusinessException(
                    "SESSION_SELF_JOIN_FORBIDDEN",
                    "본인이 만든 세션에는 합류할 수 없어요. 상대방에게 링크를 공유해 주세요",
                    400);
        }

        // Check if already joined — COMPLETED 솔로 세션 재합류는 허용 (초대 재발급 시 userBId 초기화됨)
        if (session.getUserBId() != null && !isSoloCompleted) {
            throw new BusinessException(
                    "SESSION_ALREADY_JOINED",
                    "Session already has a participant");
        }

        // Set invitee info (userBId)
        String userBId = userId.orElse(generateGuestUserId(request));
        if (!userId.isPresent()) {
            session.setInviteeGuestName(request.getNickname() != null ? request.getNickname() : "게스트");
        }
        session.setInviteeUserId(userBId);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        // Solo→Duo 전이: 트랜잭션 커밋 후 이벤트 발행 (AFTER_COMMIT)
        // join 트랜잭션이 완전히 커밋된 후 onPartnerJoined LLM 호출이 시작되어야
        // B의 첫 메시지가 도착해도 SessionRoleResolver가 userBId를 정상 인식함
        eventPublisher.publishEvent(new PartnerJoinedEvent(this, session.getId(), userBId));
        log.info("Session joined: id={}, invitee={}", session.getId(), userBId);

        return mapToSessionResponse(sessionRepository.findById(session.getId()).orElseThrow(), userBId);
    }

    /**
     * 초대 토큰 조회 (GET). 이미 발급된 토큰 재조회용.
     * 만료됐으면 새로 발급, 없으면 새로 발급, 유효하면 그대로 반환.
     * CHATTING_SOLO 상태에서만 활성.
     */
    @Transactional
    public com.againspring.api.dto.response.InviteTokenResponse getInviteForExistingSession(
            String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "세션을 찾을 수 없어요."));

        if (!session.getUserAId().equals(userId)) {
            throw new BusinessException("SESSION_FORBIDDEN", "Only session owner can view invite");
        }

        if (!session.getStatus().equals(SessionStatus.CHATTING_SOLO)) {
            throw new BusinessException(
                "SESSION_INVALID_STATE",
                "Cannot retrieve invite when session is not in SOLO state");
        }

        // 토큰 없거나 만료됐으면 새로 발급
        boolean needsNew = session.getInviteToken() == null
            || (session.getInviteExpiresAt() != null && Instant.now().isAfter(session.getInviteExpiresAt()));

        if (needsNew) {
            String newToken = "inv_" + UUID.randomUUID().toString().substring(0, 12);
            Instant expiresAt = Instant.now().plusSeconds(259200); // 72시간
            session.setInviteToken(newToken);
            session.setInviteExpiresAt(expiresAt);
            sessionRepository.save(session);
        }

        return com.againspring.api.dto.response.InviteTokenResponse.builder()
            .inviteToken(session.getInviteToken())
            .inviteExpiresAt(session.getInviteExpiresAt())
            .build();
    }

    /**
     * 채팅 도중 초대 토큰 발급 (이미 채팅 중인 사용자가 "상대 초대하기" 누름)
     * V1.5: CHATTING_SOLO 상태에서만 호출 가능
     */
    @Transactional
    public com.againspring.api.dto.response.InviteTokenResponse generateInviteForExistingSession(
            String sessionId, String userId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "세션을 찾을 수 없어요."));

        if (!session.getUserAId().equals(userId)) {
            throw new BusinessException("SESSION_FORBIDDEN", "Only session owner can invite");
        }

        // 게스트는 Solo 전용 — 초대 토큰 발급 불가
        User owner = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (owner != null && owner.isGuest()) {
            throw new BusinessException("GUEST_SOLO_ONLY",
                    "게스트는 Solo 모드만 이용 가능합니다. 회원가입 후 상대를 초대해보세요.", 403);
        }

        boolean isSoloCompleted = session.getStatus().equals(SessionStatus.COMPLETED)
                && Boolean.TRUE.equals(session.getSoloMode());
        if (!session.getStatus().equals(SessionStatus.CHATTING_SOLO) && !isSoloCompleted) {
            throw new BusinessException(
                "SESSION_INVALID_STATE",
                "Cannot invite when session is not in SOLO or completed-solo state");
        }

        // COMPLETED 솔로 세션: 새 초대 발급 시 B 참여자 정보 초기화 (재합류 허용)
        if (isSoloCompleted) {
            session.setInviteeUserId(null);
            session.setInviteeGuestName(null);
        }

        // 항상 새 토큰 발급 (COMPLETED 재합류 시 만료된 기존 토큰 갱신 포함)
        String newToken = "inv_" + UUID.randomUUID().toString().substring(0, 12);
        Instant expiresAt = Instant.now().plusSeconds(259200);  // 72시간
        session.setInviteToken(newToken);
        session.setInviteExpiresAt(expiresAt);
        sessionRepository.save(session);

        return com.againspring.api.dto.response.InviteTokenResponse.builder()
            .inviteToken(session.getInviteToken())
            .inviteExpiresAt(session.getInviteExpiresAt())
            .build();
    }

    private String generateGuestUserId(JoinSessionRequest request) {
        return "guest_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get session status (for polling).
     *
     * @param sessionId the session ID
     * @return session status response
     * @throws BusinessException if session not found
     */
    @Transactional(readOnly = true)
    public SessionStatusResponse getSessionStatus(String sessionId) {
        Session session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "세션을 찾을 수 없어요."));

        boolean hasPartnerJoined = session.getInviteeUserId() != null || session.getInviteeGuestName() != null;

        return SessionStatusResponse.builder()
                .id(session.getId())
                .status(session.getStatus().getValue())
                .currentTurn(session.getCurrentTurn())
                .hasPartnerJoined(hasPartnerJoined)
                .lastUpdatedAt(session.getUpdatedAt())
                .build();
    }

    /**
     * Delete session (soft-cancel if waiting, hard-delete if within 10 min).
     *
     * @param sessionId the session ID
     * @param userId the requesting user ID
     * @throws BusinessException if session not found or access denied
     */
    /**
     * V47: 세션 제목을 사용자가 직접 수정. titleEditedByUser=true 로 이후 자동 덮어쓰기 차단.
     */
    @Transactional
    public void updateTitle(String sessionId, String userId, String newTitle) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "세션을 찾을 수 없어요."));
        if (!session.getCreatedByUserId().equals(userId)
                && !(session.getInviteeUserId() != null && session.getInviteeUserId().equals(userId))) {
            throw new BusinessException("SESSION_FORBIDDEN", "이 세션에 접근할 권한이 없어요.");
        }
        session.setTitle(newTitle);
        session.setTitleEditedByUser(true);
        sessionRepository.save(session);
    }

    public void deleteSession(String sessionId, String userId) {
        Session session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "세션을 찾을 수 없어요."));

        boolean isCreator = session.getCreatedByUserId().equals(userId);
        boolean isInvitee = userId.equals(session.getInviteeUserId());
        if (!isCreator && !isInvitee) {
            throw new BusinessException("SESSION_FORBIDDEN", "이 세션에 접근할 권한이 없어요.");
        }

        sessionRepository.deleteById(sessionId);
        log.info("Session deleted: {} by user {}", sessionId, userId);
    }

    private SessionResponse mapToSessionResponse(Session session, String userId) {
        return SessionResponse.builder()
                .id(session.getId())
                .relationType(session.getRelationType() != null ? session.getRelationType().getValue() : null)
                .category(session.getCategory() != null
                        ? SessionResponse.CategoryInfo.builder()
                                .major(session.getCategory().majorId)
                                .customText(session.getCategory().customText)
                                .build()
                        : null)
                .status(session.getStatus().getValue())
                .currentTurn(session.getCurrentTurn())
                .currentRole(session.getCurrentRole())
                .myRole(session.getCreatedByUserId().equals(userId) ? "A" : "B")
                .partnerNickname(session.getCreatedByUserId().equals(userId)
                        ? (session.getInviteeUserId() != null
                                ? userRepository.findByIdAndDeletedAtIsNull(session.getInviteeUserId())
                                        .map(User::getNickname)
                                        .orElse(null)
                                : session.getInviteeGuestName())
                        : (userRepository.findByIdAndDeletedAtIsNull(session.getCreatedByUserId())
                                .map(User::getNickname)
                                .orElse(null)))
                // V1.5: turns 제거 (Message 테이블 사용)
                .turns(new ArrayList<>())
                .createdAt(session.getCreatedAt())
                .finalizeAgreedByA(session.getFinalizeAgreedByA())
                .finalizeAgreedByB(session.getFinalizeAgreedByB())
                .reportId(session.getReportId())
                .build();
    }

    private SessionListItemResponse mapToSessionListItem(Session session) {
        String partnerName = session.getInviteeUserId() != null
                ? userRepository.findByIdAndDeletedAtIsNull(session.getInviteeUserId())
                        .map(User::getNickname)
                        .orElse(session.getInviteeGuestName())
                : session.getInviteeGuestName();

        return SessionListItemResponse.builder()
                .id(session.getId())
                .relationType(session.getRelationType() != null ? session.getRelationType().getValue() : null)
                .partnerName(partnerName)
                .status(session.getStatus().getValue())
                .createdAt(session.getCreatedAt())
                .completedAt(session.getCompletedAt())
                .build();
    }
}
