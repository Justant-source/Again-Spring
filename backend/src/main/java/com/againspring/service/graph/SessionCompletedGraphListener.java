package com.againspring.service.graph;

import com.againspring.domain.Report;
import com.againspring.domain.Session;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.service.event.SessionCompletedEvent;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 세션 완료 이벤트를 받아 SQL 관계 그래프에 기록한다.
 * Why: 같은 사람과의 반복 세션을 추적해 관계 온도 추이/이력을 보여주기 위함.
 * How: SessionCompletedEvent 수신 → Session + Report 로드 → recordConflict(userA, userB).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionCompletedGraphListener {

    private final RelationshipGraphService relationshipGraphService;
    private final SessionRepository sessionRepository;
    private final ReportRepository reportRepository;

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

        double temperature = reportRepository.findBySessionId(sessionId)
                .map(this::extractTemperature)
                .orElse(36.5);

        Instant startedAt = session.getCreatedAt() != null ? session.getCreatedAt() : Instant.now();
        Instant endedAt = event.getCompletedAt() != null ? event.getCompletedAt() : Instant.now();

        // userA, userB: 실제 사용자 ID (displayName 불필요, 정규화는 service에서 처리)
        relationshipGraphService.recordConflict(
                session.getCreatedByUserId(),
                session.getInviteeUserId(),
                session.getRelationType(),
                sessionId,
                session.getConflictType(),
                temperature,
                startedAt,
                endedAt
        );

        log.info("Recorded session {} conflict into relationship graph (A={}, B={}, temp={})",
                sessionId,
                session.getCreatedByUserId(),
                session.getInviteeUserId(),
                temperature);
    }

    private double extractTemperature(Report report) {
        if (report.getTemperature() == null) {
            return 36.5;
        }
        return report.getTemperature();
    }
}
