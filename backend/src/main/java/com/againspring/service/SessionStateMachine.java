package com.againspring.service;

import com.againspring.domain.enums.SessionStatus;
import org.springframework.stereotype.Component;

/**
 * Session state machine (V1.5 카톡식 채팅)
 * Validates legal state transitions for single-flow chat model
 */
@Component
public class SessionStateMachine {

    /**
     * Check if transition from→to is allowed
     */
    public boolean canTransition(SessionStatus from, SessionStatus to) {
        if (from == to) return true;  // Idempotent

        return switch (from) {
            case CHATTING_SOLO ->
                to == SessionStatus.CHATTING_DUO        // 상대 join
                || to == SessionStatus.COMPLETED         // 솔로 종료 (본인 ≥3턴)
                || to == SessionStatus.TERMINATED;       // 위기

            case CHATTING_DUO ->
                to == SessionStatus.AWAITING_FINALIZATION
                || to == SessionStatus.TERMINATED;

            case AWAITING_FINALIZATION ->
                to == SessionStatus.COMPLETED            // 양쪽 동의
                || to == SessionStatus.CHATTING_DUO       // 한쪽 거부 → 채팅 복귀
                || to == SessionStatus.TERMINATED;

            case COMPLETED, TERMINATED -> false;

            // 운영 호환 (기존 6턴 세션이 있으면 종료 처리만 허용)
            case WAITING_B, B_JOINED, IN_MEDIATION, SOLO_MODE ->
                to == SessionStatus.COMPLETED || to == SessionStatus.TERMINATED;
        };
    }

    /**
     * 세션이 활성 상태인지 확인
     */
    public boolean isActive(SessionStatus status) {
        return switch (status) {
            case CHATTING_SOLO, CHATTING_DUO, AWAITING_FINALIZATION -> true;
            default -> false;
        };
    }

    /**
     * 세션이 종료 상태인지 확인
     */
    public boolean isTerminal(SessionStatus status) {
        return status == SessionStatus.COMPLETED || status == SessionStatus.TERMINATED;
    }

    /**
     * Duo 상태인지 확인 (상대와 함께하는 상태)
     */
    public boolean isDuo(SessionStatus status) {
        return status == SessionStatus.CHATTING_DUO
            || status == SessionStatus.AWAITING_FINALIZATION;
    }
}
