package com.againspring.service;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import com.againspring.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SessionRoleResolver (V1.5)
 * 사용자 ID를 기반으로 메시지 발신자(USER_A/USER_B) 결정
 */
@Component
@RequiredArgsConstructor
public class SessionRoleResolver {
    private final SessionRepository sessionRepo;

    /**
     * 사용자의 역할(USER_A/USER_B)을 결정
     */
    public MessageSender resolveSender(String sessionId, String userId) {
        Session s = sessionRepo.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (userId.equals(s.getUserAId())) return MessageSender.USER_A;
        if (userId.equals(s.getUserBId())) return MessageSender.USER_B;

        throw new IllegalStateException("User not part of this session: " + userId);
    }
}
