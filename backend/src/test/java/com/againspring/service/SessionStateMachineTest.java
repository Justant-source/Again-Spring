package com.againspring.service;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.enums.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SessionStateMachine.
 */
class SessionStateMachineTest {

    private SessionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new SessionStateMachine();
    }

    @Test
    void testValidTransitionWaitingBToB_Joined() {
        // Given & When & Then (should not throw)
        assertThatNoException()
                .isThrownBy(() ->
                        stateMachine.validateTransition(
                                SessionStatus.WAITING_B,
                                SessionStatus.B_JOINED));
    }

    @Test
    void testValidTransitionWaitingBToSoloMode() {
        assertThatNoException()
                .isThrownBy(() ->
                        stateMachine.validateTransition(
                                SessionStatus.WAITING_B,
                                SessionStatus.SOLO_MODE));
    }

    @Test
    void testValidTransitionB_JoinedToInMediation() {
        assertThatNoException()
                .isThrownBy(() ->
                        stateMachine.validateTransition(
                                SessionStatus.B_JOINED,
                                SessionStatus.IN_MEDIATION));
    }

    @Test
    void testValidTransitionInMediationToCompleted() {
        assertThatNoException()
                .isThrownBy(() ->
                        stateMachine.validateTransition(
                                SessionStatus.IN_MEDIATION,
                                SessionStatus.COMPLETED));
    }

    @Test
    void testInvalidTransitionWaitingBToInMediation() {
        // When & Then
        assertThatThrownBy(() ->
                stateMachine.validateTransition(
                        SessionStatus.WAITING_B,
                        SessionStatus.IN_MEDIATION))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SESSION_INVALID_STATE");
    }

    @Test
    void testInvalidTransitionCompletedToB_Joined() {
        assertThatThrownBy(() ->
                stateMachine.validateTransition(
                        SessionStatus.COMPLETED,
                        SessionStatus.B_JOINED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void testIdempotentTransition() {
        // Same status should be allowed
        assertThatNoException()
                .isThrownBy(() ->
                        stateMachine.validateTransition(
                                SessionStatus.IN_MEDIATION,
                                SessionStatus.IN_MEDIATION));
    }
}
