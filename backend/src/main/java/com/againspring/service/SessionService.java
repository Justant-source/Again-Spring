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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session service for CRUD operations.
 * Includes state machine validation and keyword guard integration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final KeywordGuard keywordGuard;
    private final SessionStateMachine stateMachine;

    private static final long INVITE_TOKEN_TTL_MS = 86400000; // 24 hours
    private static final long CONTENT_EXPIRY_TTL_MS = 2592000000L; // 30 days

    /**
     * Create a new session (A initiates).
     *
     * @param createdByUserId the creator user ID
     * @param request create request
     * @return create session response with invite token
     * @throws BusinessException if description contains crisis keywords
     */
    public CreateSessionResponse createSession(String createdByUserId, CreateSessionRequest request) {
        // A는 온보딩(성격검사)을 반드시 완료해야 세션을 생성할 수 있음
        User creator = userRepository.findByIdAndDeletedAtIsNull(createdByUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다"));
        if (creator.getOnboardingCompletedAt() == null) {
            throw new BusinessException("ONBOARDING_REQUIRED", "성격검사를 먼저 완료해주세요", 403);
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
            category.majorId = request.getCategory().getMajor();
            category.middleId = request.getCategory().getMiddle();
            category.minorId = request.getCategory().getMinor();
            category.customText = request.getCategory().getCustomMinor();
        }

        // Determine initial status
        SessionStatus initialStatus = (request.getSoloMode() != null && request.getSoloMode())
                ? SessionStatus.SOLO_MODE
                : SessionStatus.WAITING_B;

        // Create session
        String sessionId = "ses_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Session session = Session.builder()
                .id(sessionId)
                .inviteToken(inviteToken)
                .inviteExpiresAt(expiresAt)
                .createdByUserId(createdByUserId)
                .relationType(RelationType.fromValue(request.getRelationType()))
                .category(category)
                .status(initialStatus)
                .currentTurn(1)
                .currentRole("A")
                .soloMode(request.getSoloMode() != null && request.getSoloMode())
                .turns(new ArrayList<>())
                .crisisDetections(new ArrayList<>())
                .createdAt(now)
                .updatedAt(now)
                .contentExpiresAt(now.plusMillis(CONTENT_EXPIRY_TTL_MS))
                .build();

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
     *
     * @param inviteToken the invite token
     * @param request join request
     * @param userId the joining user ID (may be null for guests)
     * @return session response
     * @throws BusinessException if token invalid/expired or session already joined
     */
    public SessionResponse joinSession(String inviteToken, JoinSessionRequest request, Optional<String> userId) {
        Session session = sessionRepository
                .findByInviteToken(inviteToken)
                .orElseThrow(() -> new BusinessException("INVITE_TOKEN_INVALID", "Invalid invite token"));

        // Check expiry
        if (Instant.now().isAfter(session.getInviteExpiresAt())) {
            throw new BusinessException("INVITE_TOKEN_INVALID", "Invite token has expired");
        }

        // Check status
        if (!session.getStatus().equals(SessionStatus.WAITING_B)) {
            throw new BusinessException(
                    "SESSION_INVALID_STATE",
                    "Session is no longer waiting for B");
        }

        // Check if already joined
        if (session.getInviteeUserId() != null || session.getInviteeGuestName() != null) {
            throw new BusinessException(
                    "SESSION_ALREADY_JOINED",
                    "Session already has a participant");
        }

        // Set invitee info
        if (userId.isPresent()) {
            session.setInviteeUserId(userId.get());
        } else {
            session.setInviteeGuestName(request.getNickname() != null ? request.getNickname() : "게스트");
        }

        // Transition state
        stateMachine.validateTransition(session.getStatus(), SessionStatus.B_JOINED);
        session.setStatus(SessionStatus.B_JOINED);
        session.setUpdatedAt(Instant.now());

        Session saved = sessionRepository.save(session);
        log.info("Session joined: id={}, invitee={}", saved.getId(), userId.orElse("guest"));

        return mapToSessionResponse(saved, userId.orElse("guest"));
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

        // Only creator can delete
        if (!session.getCreatedByUserId().equals(userId)) {
            throw new BusinessException("SESSION_FORBIDDEN", "Only creator can delete");
        }

        long ageMs = Instant.now().toEpochMilli() - session.getCreatedAt().toEpochMilli();
        long tenMinutesMs = 600000;

        if (ageMs <= tenMinutesMs && session.getStatus().equals(SessionStatus.WAITING_B)) {
            // Hard delete if within 10 min
            sessionRepository.deleteById(sessionId);
            log.info("Session hard-deleted: {}", sessionId);
        } else if (session.getStatus().equals(SessionStatus.WAITING_B)) {
            // Soft cancel if past 10 min
            session.setStatus(SessionStatus.TERMINATED);
            sessionRepository.save(session);
            log.info("Session soft-cancelled: {}", sessionId);
        } else {
            throw new BusinessException(
                    "SESSION_INVALID_STATE",
                    "Cannot delete session in current state");
        }
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
                .turns(session.getTurns().stream()
                        .map(turn -> SessionResponse.TurnInfo.builder()
                                .turnNumber(turn.getTurnNumber())
                                .role(turn.getRole() != null ? turn.getRole().getValue() : null)
                                .mediatorMessage(turn.getMediatorMessage())
                                .myTurn(turn.getUserId() != null && turn.getUserId().equals(userId))
                                .completed(turn.getCreatedAt() != null)
                                .createdAt(turn.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(session.getCreatedAt())
                .build();
    }

    private SessionListItemResponse mapToSessionListItem(Session session) {
        String partnerName = session.getCreatedByUserId().equals(session.getCreatedByUserId())
                ? (session.getInviteeUserId() != null
                        ? userRepository.findByIdAndDeletedAtIsNull(session.getInviteeUserId())
                                .map(User::getNickname)
                                .orElse(null)
                        : session.getInviteeGuestName())
                : userRepository.findByIdAndDeletedAtIsNull(session.getCreatedByUserId())
                        .map(User::getNickname)
                        .orElse(null);

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
