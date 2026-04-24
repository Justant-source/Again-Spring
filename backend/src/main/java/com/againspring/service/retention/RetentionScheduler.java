package com.againspring.service.retention;

import com.againspring.domain.Session;
import com.againspring.domain.Turn;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.TurnRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터 보관 정책 실행자
 * 매일 3am KST 실행: 30일 경과한 세션의 콘텐츠 정제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionScheduler {

    private final SessionRepository sessionRepository;
    private final TurnRepository turnRepository;

    /**
     * 일일 정제 작업 (3am KST)
     * cron = "0 0 3 * * *" => 매일 3:00:00
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredContent() {
        log.info("Starting daily content purge job");

        try {
            Instant now = Instant.now();

            // Find sessions where contentExpiresAt has passed
            // AND status is COMPLETED or TERMINATED
            List<Session> sessions = sessionRepository.findExpiredForRetention(
                    List.of(SessionStatus.COMPLETED, SessionStatus.TERMINATED),
                    now
            );
            log.debug("Found {} sessions eligible for content purge", sessions.size());

            for (Session session : sessions) {
                purgeSessionContent(session);
            }

            log.info("Content purge job completed. Purged {} sessions", sessions.size());
        } catch (Exception e) {
            log.error("Error during content purge job", e);
        }
    }

    /**
     * 특정 세션의 콘텐츠를 정제
     * turns[].content, turns[].mediatorMessage, turns[].mediatorSummaryForOpponent 을 null로 설정
     * 하지만 메타데이터(createdAt, turnNumber, role 등)는 유지
     */
    private void purgeSessionContent(Session session) {
        try {
            if (session.getId() == null) {
                return;
            }

            List<Turn> turns = turnRepository.findBySessionIdOrderByTurnNumberAsc(session.getId());
            if (turns == null || turns.isEmpty()) {
                return; // Nothing to purge
            }

            // Clear sensitive fields from all turns
            for (Turn turn : turns) {
                turn.setContent(null);
                turn.setMediatorMessage(null);
                turn.setMediatorSummaryForOpponent(null);
                turnRepository.save(turn);
            }

            log.debug("Purged content from session {}", session.getId());
        } catch (Exception e) {
            log.error("Error purging content from session {}", session.getId(), e);
        }
    }

}
