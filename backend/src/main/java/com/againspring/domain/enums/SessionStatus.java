package com.againspring.domain.enums;

/**
 * 세션 상태 (DATABASE_SCHEMA.md + BACKEND_WORK_ORDER.md Phase 5.4)
 * waiting_b | b_joined | in_mediation | completed | solo_mode | terminated
 */
public enum SessionStatus {
    WAITING_B("waiting_b"),
    B_JOINED("b_joined"),
    IN_MEDIATION("in_mediation"),
    COMPLETED("completed"),
    SOLO_MODE("solo_mode"),
    TERMINATED("terminated");

    private final String value;

    SessionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SessionStatus fromValue(String value) {
        for (SessionStatus status : SessionStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown SessionStatus: " + value);
    }
}
