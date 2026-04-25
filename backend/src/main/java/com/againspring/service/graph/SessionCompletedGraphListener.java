package com.againspring.service.graph;

import com.againspring.domain.Session;
import com.againspring.repository.SessionRepository;
import com.againspring.service.event.SessionCompletedEvent;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCompletedGraphListener {

    private final RelationshipGraphService relationshipGraphService;
    private final SessionRepository sessionRepository;

    @Async
    @EventListener
    public void onSessionCompleted(SessionCompletedEvent event) {
        String sessionId = event.getSessionId();
        Optional<Session> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("SessionCompletedEvent received but session {} not found", sessionId);
            return;
        }
        Session session = sessionOpt.get();

        // Solo mode 또는 inviteeUserId가 null이면 스킵
        if (Boolean.TRUE.equals(session.getSoloMode()) || session.getInviteeUserId() == null) {
            log.info("Skipping relationship recording for solo/null session {}", sessionId);
            return;
        }

        Instant startedAt = session.getCreatedAt() != null ? session.getCreatedAt() : Instant.now();
        Instant endedAt = event.getCompletedAt() != null ? event.getCompletedAt() : Instant.now();

        relationshipGraphService.recordConflict(
                session.getCreatedByUserId(),
                session.getInviteeUserId(),
                session.getRelationType(),
                sessionId,
                session.getConflictType(),
                startedAt,
                endedAt
        );

        log.info("Recorded session {} conflict into relationship graph (A={}, B={})",
                sessionId,
                session.getCreatedByUserId(),
                session.getInviteeUserId());
    }
}
