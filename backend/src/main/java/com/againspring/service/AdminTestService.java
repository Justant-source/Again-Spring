package com.againspring.service;

import com.againspring.domain.enums.SessionStatus;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTestService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public Map<String, Integer> resetTestUserData() {
        List<String> testUserIds = userRepository.findByEmailStartingWith("test")
                .stream().map(u -> u.getId()).toList();

        log.info("Resetting data for {} test users", testUserIds.size());
        if (testUserIds.isEmpty()) {
            return Map.of("users_checked", 0, "sessions", 0, "messages", 0, "reports", 0);
        }

        List<String> sessionIds = sessionRepository.findAll().stream()
                .filter(s -> testUserIds.contains(s.getCreatedByUserId()) ||
                             testUserIds.contains(s.getInviteeUserId()))
                .map(s -> s.getId())
                .toList();

        log.info("Deleting {} sessions and their messages/reports", sessionIds.size());

        AtomicInteger messageCount = new AtomicInteger(0);
        AtomicInteger reportCount = new AtomicInteger(0);

        for (String sessionId : sessionIds) {
            var msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            messageCount.addAndGet(msgs.size());
            messageRepository.deleteAll(msgs);

            reportRepository.findBySessionId(sessionId).ifPresent(r -> {
                reportRepository.delete(r);
                reportCount.incrementAndGet();
            });
        }

        sessionRepository.deleteAllById(sessionIds);

        return Map.of(
                "users_checked", testUserIds.size(),
                "sessions", sessionIds.size(),
                "messages", messageCount.get(),
                "reports", reportCount.get()
        );
    }

    /** 특정 세션을 TERMINATED 상태로 강제 종료 (테스트 cleanup 전용). */
    @Transactional
    public void terminateSession(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(SessionStatus.TERMINATED);
            session.setCompletedAt(Instant.now());
            sessionRepository.save(session);
            log.info("Test cleanup: session {} -> TERMINATED", sessionId);
        });
    }
}
