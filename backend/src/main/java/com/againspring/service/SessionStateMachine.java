package com.againspring.service;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.enums.SessionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Session state machine.
 * Validates legal state transitions.
 */
@Component
@Slf4j
public class SessionStateMachine {

    /**
     * Validate transition from one state to another.
     *
     * @param fromStatus current status
     * @param toStatus target status
     * @throws BusinessException if transition is invalid
     */
    public void validateTransition(SessionStatus fromStatus, SessionStatus toStatus) {
        boolean valid = isValidTransition(fromStatus, toStatus);

        if (!valid) {
            String msg = String.format("Invalid state transition: %s -> %s",
                    fromStatus.getValue(), toStatus.getValue());
            log.warn(msg);
            throw new BusinessException("SESSION_INVALID_STATE", msg);
        }
    }

    private boolean isValidTransition(SessionStatus from, SessionStatus to) {
        if (from == to) {
            return true; // Idempotent
        }

        return switch (from) {
            case WAITING_B -> to == SessionStatus.B_JOINED
                    || to == SessionStatus.SOLO_MODE
                    || to == SessionStatus.TERMINATED;

            case B_JOINED -> to == SessionStatus.IN_MEDIATION
                    || to == SessionStatus.COMPLETED
                    || to == SessionStatus.TERMINATED;

            case IN_MEDIATION -> to == SessionStatus.COMPLETED
                    || to == SessionStatus.TERMINATED;

            case COMPLETED, SOLO_MODE, TERMINATED -> false; // Terminal states

            default -> false;
        };
    }
}
