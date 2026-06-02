package com.againspring.service;

import com.againspring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Admin test service for resetting test data.
 * NOTE: Session/Message/Report classes removed due to deletion of mediation code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTestService {

    private final UserRepository userRepository;

    @Transactional
    public Map<String, Integer> resetTestUserData() {
        List<String> testUserIds = userRepository.findByEmailStartingWith("test")
                .stream().map(u -> u.getId()).toList();

        log.info("Resetting data for {} test users", testUserIds.size());
        if (testUserIds.isEmpty()) {
            return Map.of("users_checked", 0, "sessions", 0, "messages", 0, "reports", 0);
        }

        // NOTE: Session/Message/Report deletion removed due to deletion of those classes
        // TODO: Implement test data cleanup for deleted infrastructure
        log.warn("Test user data cleanup not yet implemented for refactored architecture");

        return Map.of(
                "users_checked", testUserIds.size(),
                "sessions", 0,
                "messages", 0,
                "reports", 0
        );
    }

    /** Stub: session termination not implemented. */
    @Transactional
    public void terminateSession(String sessionId) {
        log.warn("Session termination not yet implemented for sessionId={}", sessionId);
        // TODO: Implement session cleanup for deleted infrastructure
    }
}
