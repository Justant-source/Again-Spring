package com.againspring.service.retention;

import com.againspring.domain.Session;
import com.againspring.domain.Message;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.MessageRepository;
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
    private final MessageRepository messageRepository;

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
