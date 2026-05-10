package com.againspring.service.retention;

import com.againspring.config.UserPermissionsConfig;
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
 * 게스트 세션 메시지 본문 자동 NULL 처리 (개인정보 보호).
 * 보존 일수는 user-permissions.json의 tiers.guest.data.messageContentRetentionDays.
 * 매일 3:30 KST 실행.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestSessionCleanupScheduler {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final UserPermissionsConfig permissions;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeOldGuestSessions() {
        Integer retentionDays = permissions.getGuest().getData().getMessageContentRetentionDays();
        if (retentionDays == null || retentionDays <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<Session> oldSessions = sessionRepository.findOldGuestSessions(cutoff);

        if (oldSessions.isEmpty()) {
            return;
        }

        log.info("Purging {} old guest sessions (older than {} days)", oldSessions.size(), retentionDays);
        List<String> sessionIds = oldSessions.stream().map(Session::getId).toList();

        messageRepository.nullifyContentBySessionIds(sessionIds);
        log.info("Nullified message content for {} guest sessions", sessionIds.size());
    }
}
