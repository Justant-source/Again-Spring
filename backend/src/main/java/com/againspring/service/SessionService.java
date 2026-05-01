package com.againspring.service;

import com.againspring.api.dto.request.CreateSessionRequest;
import com.againspring.api.dto.request.JoinSessionRequest;
import com.againspring.api.dto.response.CreateSessionResponse;
import com.againspring.api.dto.response.SessionListItemResponse;
import com.againspring.api.dto.response.SessionResponse;
import com.againspring.api.dto.response.SessionStatusResponse;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.enums.RelationType;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.ScanResult;
import com.againspring.service.event.PartnerJoinedEvent;
import java.time.Instant;
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

    public SessionService(SessionRepository sessionRepository,
                          UserRepository userRepository,
                          KeywordGuard keywordGuard,
                          SessionStateMachine stateMachine,
                          @Lazy ChatService chatService,
                          ApplicationEventPublisher eventPublisher) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.keywordGuard = keywordGuard;
        this.stateMachine = stateMachine;
        this.chatService = chatService;
        this.eventPublisher = eventPublisher;
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
    public CreateSessionResponse createSession(String createdByUserId, CreateSessionRequest request) {
        User creator = userRepository.findByIdAndDeletedAtIsNull(createdByUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다"));
        // 게스트는 온보딩 완료 여부 체크 없이 세션 생성 허용 (앱 내 10문항 흐름 병행)
        if (!creator.isGuest() && creator.getOnboardingCompletedAt() == null) {
            throw new BusinessException("ONBOARDING_REQUIRED", "성격검사를 먼저 완료해주세요", 403);
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

        // Validate relation type
        try {
            RelationType.fromValue(request.getRelationType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_INPUT", "Invalid relation type");
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

        // Build category
        Session.Category category = null;
        if (request.getCategory() != null) {
            category = new Session.Category();
            category.majorId = request.getCategory().getMajorId();
            category.middleId = request.getCategory().getMiddleId();
            category.minorId = request.getCategory().getMinorId();
            category.customText = request.getCategory().getCustomText();
        }

        // V1.5: 모든 세션은 CHATTING_SOLO로 시작 (초대 여부 무관)
        // 상대 join은 별도 메서드로 처리 (generateInviteForExistingSession)
        String sessionId = "ses_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Session session = Session.builder()
                .id(sessionId)
                .createdByUserId(createdByUserId)
                .relationType(RelationType.fromValue(request.getRelationType()))
                .category(category)
                .status(SessionStatus.CHATTING_SOLO)  // V1.5: 항상 SOLO로 시작
                .soloMode(true)                         // V1.5: default true
                .userAMessageCount(0)
                .userBMessageCount(0)
                .finalizeAgreedByA(false)
                .finalizeAgreedByB(false)
                .mediatorStyleX(request.getMediatorStyleX() != null ? request.getMediatorStyleX() : 50)
                .mediatorStyleY(request.getMediatorStyleY() != null ? request.getMediatorStyleY() : 50)
                .crisisDetections(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .contentExpiresAt(now.plusMillis(CONTENT_EXPIRY_TTL_MS))
                .build();

        // V1.5: 초대 토큰은 필요시에 generateInviteForExistingSession으로 생성
        // (createSession 단계에서는 생성하지 않음)

        Session saved = sessionRepository.save(session);
        log.info("Session created: id={}, token={}, creator={}", saved.getId(), inviteToken, createdByUserId);

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
        Session session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));

        // Access control: creator or invitee only
        if (!session.getCreatedByUserId().equals(userId)
                && !((session.getInviteeUserId() != null && session.getInviteeUserId().equals(userId)))) {
            throw new BusinessException("SESSION_FORBIDDEN", "Access denied to this session");
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
                .orElseThrow(() -> new BusinessException("INVITE_TOKEN_INVALID", "Invalid invite token"));

        // Check expiry
        if (session.getInviteExpiresAt() != null && Instant.now().isAfter(session.getInviteExpiresAt())) {
            throw new BusinessException("INVITE_TOKEN_EXPIRED", "Invite token has expired");
        }

        // V1.5: CHATTING_SOLO 상태에서만 join 가능
        if (!session.getStatus().equals(SessionStatus.CHATTING_SOLO)) {
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

        // Check if already joined (userB가 이미 있으면 안됨)
        if (session.getUserBId() != null) {
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
            .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));

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
            .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));

        if (!session.getUserAId().equals(userId)) {
            throw new BusinessException("SESSION_FORBIDDEN", "Only session owner can invite");
        }

        boolean isSoloCompleted = session.getStatus().equals(SessionStatus.COMPLETED)
                && Boolean.TRUE.equals(session.getSoloMode());
        if (!session.getStatus().equals(SessionStatus.CHATTING_SOLO) && !isSoloCompleted) {
            throw new BusinessException(
                "SESSION_INVALID_STATE",
                "Cannot invite when session is not in SOLO or completed-solo state");
        }

        // 이미 토큰이 있으면 재사용, 없으면 새로 발급
        if (session.getInviteToken() == null) {
            String newToken = "inv_" + UUID.randomUUID().toString().substring(0, 12);
            Instant expiresAt = Instant.now().plusSeconds(259200);  // 72시간
            session.setInviteToken(newToken);
            session.setInviteExpiresAt(expiresAt);
            sessionRepository.save(session);
        }

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
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));

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
    public void deleteSession(String sessionId, String userId) {
        Session session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "Session not found"));

        boolean isCreator = session.getCreatedByUserId().equals(userId);
        boolean isInvitee = userId.equals(session.getInviteeUserId());
        if (!isCreator && !isInvitee) {
            throw new BusinessException("SESSION_FORBIDDEN", "Access denied to this session");
        }

        sessionRepository.deleteById(sessionId);
        log.info("Session deleted: {} by user {}", sessionId, userId);
    }

    private SessionResponse mapToSessionResponse(Session session, String userId) {
        return SessionResponse.builder()
                .id(session.getId())
                .relationType(session.getRelationType().getValue())
                .category(session.getCategory() != null
                        ? SessionResponse.CategoryInfo.builder()
                                .major(session.getCategory().majorId)
                                .middle(session.getCategory().middleId)
                                .minor(session.getCategory().minorId)
                                .customMinor(session.getCategory().customText)
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
                .relationType(session.getRelationType().getValue())
                .partnerName(partnerName)
                .status(session.getStatus().getValue())
                .createdAt(session.getCreatedAt())
                .completedAt(session.getCompletedAt())
                .build();
    }
}
