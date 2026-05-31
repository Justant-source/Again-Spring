package com.againspring.service.community;

import com.againspring.domain.community.ThreeWaySession;
import com.againspring.domain.enums.ThreeWayStatus;
import com.againspring.repository.community.ThreeWaySessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing 3-way mediation sessions (V17 Phase 6).
 * Handles session creation, joining, and retrieval.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ThreeWaySessionService {

    private final ThreeWaySessionRepository twsRepo;

    /**
     * Creates a new 3-way session initiated by Party A.
     *
     * @param partyAUserId User ID of Party A
     * @param category Category/topic of the mediation
     * @return Created ThreeWaySession with WAITING status
     */
    public ThreeWaySession create(String partyAUserId, String category) {
        String id = "tws_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String token = UUID.randomUUID().toString().replace("-", "");

        ThreeWaySession session = ThreeWaySession.builder()
            .id(id)
            .partyAUserId(partyAUserId)
            .category(category)
            .status(ThreeWayStatus.WAITING)
            .inviteToken(token)
            .build();

        ThreeWaySession saved = twsRepo.save(session);
        log.info("Three-way session created: id={}, partyA={}, category={}", id, partyAUserId, category);
        return saved;
    }

    /**
     * Party B joins an existing 3-way session using invite token.
     *
     * @param inviteToken Invite token
     * @param partyBUserId User ID of Party B
     * @return Updated ThreeWaySession with ACTIVE status
     * @throws RuntimeException if token not found or session already has Party B
     */
    public ThreeWaySession join(String inviteToken, String partyBUserId) {
        ThreeWaySession session = twsRepo.findByInviteToken(inviteToken)
            .orElseThrow(() -> new RuntimeException("INVITE_NOT_FOUND"));

        if (session.getStatus() != ThreeWayStatus.WAITING) {
            throw new IllegalStateException("SESSION_ALREADY_ACTIVE_OR_CLOSED");
        }

        if (session.getPartyBUserId() != null) {
            throw new IllegalStateException("PARTY_B_ALREADY_JOINED");
        }

        session.setPartyBUserId(partyBUserId);
        session.setStatus(ThreeWayStatus.ACTIVE);
        ThreeWaySession updated = twsRepo.save(session);

        log.info("Party B joined three-way session: id={}, partyB={}", session.getId(), partyBUserId);
        return updated;
    }

    /**
     * Closes an active 3-way session.
     *
     * @param twsId Session ID
     */
    public void close(String twsId) {
        ThreeWaySession session = twsRepo.findById(twsId)
            .orElseThrow(() -> new RuntimeException("SESSION_NOT_FOUND"));

        session.setStatus(ThreeWayStatus.CLOSED);
        twsRepo.save(session);
        log.info("Three-way session closed: id={}", twsId);
    }

    /**
     * Retrieves a session by ID with access control.
     *
     * @param twsId Session ID
     * @param userId User ID to verify access
     * @return ThreeWaySession
     * @throws org.springframework.security.access.AccessDeniedException if user is not a participant
     */
    @Transactional(readOnly = true)
    public ThreeWaySession getSession(String twsId, String userId) {
        ThreeWaySession session = twsRepo.findById(twsId)
            .orElseThrow(() -> new RuntimeException("SESSION_NOT_FOUND"));

        if (!isParticipant(session, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("NOT_PARTICIPANT");
        }

        return session;
    }

    /**
     * Lists all sessions where the user is a participant.
     *
     * @param userId User ID
     * @return List of ThreeWaySessions
     */
    @Transactional(readOnly = true)
    public List<ThreeWaySession> getSessionsByUser(String userId) {
        return twsRepo.findByPartyAUserIdOrPartyBUserId(userId, userId);
    }

    /**
     * Verifies if a user is a participant in the session.
     *
     * @param session ThreeWaySession
     * @param userId User ID
     * @return true if user is Party A or Party B
     */
    public boolean isParticipant(ThreeWaySession session, String userId) {
        return userId.equals(session.getPartyAUserId()) || userId.equals(session.getPartyBUserId());
    }
}
