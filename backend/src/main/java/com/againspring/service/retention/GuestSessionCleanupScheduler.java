package com.againspring.service.retention;

import com.againspring.domain.Session;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 게스트 세션 7일 만료 정리.
 * 매일 3:30 KST 실행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestSessionCleanupScheduler {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeOldGuestSessions() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Session> oldSessions = sessionRepository.findOldGuestSessions(cutoff);

        if (oldSessions.isEmpty()) {
            return;
        }

        log.info("Purging {} old guest sessions (older than 7 days)", oldSessions.size());
        List<String> sessionIds = oldSessions.stream().map(Session::getId).toList();

        // 메시지 본문 NULL 처리 (개인정보 보호)
        messageRepository.nullifyContentBySessionIds(sessionIds);
        log.info("Nullified message content for {} guest sessions", sessionIds.size());
    }
}
