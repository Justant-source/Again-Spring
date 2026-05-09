package com.againspring.service.retention;

import com.againspring.domain.Session;
import com.againspring.domain.Message;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.FeedbackRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final MessageRepository messageRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final EntityManager em; // Phase D PR-3 — Phase D 컬럼 native 만료 처리

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

            // Phase D PR-3 — Phase D JSON 컬럼 만료 처리
            // issue_context는 headline만 보존(리포트에 가까운 요약), 나머지는 NULL
            // data-retention.md §"30일 원문 만료" 준수
            int phaseDPurged = em.createNativeQuery(
                "UPDATE sessions SET " +
                "user_state_history = NULL, " +
                "question_queue_a = NULL, " +
                "question_queue_b = NULL, " +
                "issue_context = CASE " +
                "  WHEN JSON_EXTRACT(issue_context, '$.headline') IS NOT NULL " +
                "  THEN JSON_OBJECT('headline', JSON_UNQUOTE(JSON_EXTRACT(issue_context, '$.headline'))) " +
                "  ELSE NULL END " +
                "WHERE status IN ('COMPLETED', 'TERMINATED') " +
                "AND content_expires_at < :threshold")
                .setParameter("threshold", now)
                .executeUpdate();
            log.info("Phase D context purge completed. Updated {} sessions", phaseDPurged);

            log.info("Content purge job completed. Purged {} sessions", sessions.size());
        } catch (Exception e) {
            log.error("Error during content purge job", e);
        }
    }

    /**
     * 6개월 경과 피드백 내용 삭제 (집계 메타는 daily_stats에 보존)
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purge6MonthFeedbackContent() {
        Instant cutoff = Instant.now().minus(180, ChronoUnit.DAYS);
        feedbackRepository.findAll().stream()
                .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().isBefore(cutoff) && f.getContent() != null)
                .forEach(f -> {
                    f.setContent("[삭제됨]");
                    feedbackRepository.save(f);
                });
        log.info("6-month feedback content purge completed");
    }

    /**
     * 특정 세션의 콘텐츠를 정제 (V1.5)
     * messages[].content를 null로 설정하되, 메타데이터(createdAt, sender 등)는 유지
     */
    private void purgeSessionContent(Session session) {
        try {
            if (session.getId() == null) {
                return;
            }

            // V1.5: Message 테이블에서 콘텐츠 정제
            // 구현: messageRepository에서 sessionId의 모든 메시지 조회 후 content null 처리
            List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
            for (Message msg : messages) {
                msg.setContent(null);
                messageRepository.save(msg);
            }

            log.debug("Purged {} messages from session {}", messages.size(), session.getId());
        } catch (Exception e) {
            log.error("Error purging content from session {}", session.getId(), e);
        }
    }
}
