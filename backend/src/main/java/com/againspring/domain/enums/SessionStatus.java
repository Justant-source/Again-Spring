package com.againspring.domain.enums;

/**
 * 세션 상태 (V1.5 단일 흐름 카톡식)
 * chatting_solo | chatting_duo | awaiting_finalization | completed | terminated
 *
 * 기존 6턴 상태들(waiting_b, b_joined, in_mediation, solo_mode)은 운영 호환만
 */
public enum SessionStatus {
    // V1.5 신규 상태
    CHATTING_SOLO("chatting_solo"),                  // 본인만 AI와 채팅 (초대 발송 여부 무관)
    CHATTING_DUO("chatting_duo"),                    // 상대 합류 후 양쪽이 AI와 채팅
    AWAITING_FINALIZATION("awaiting_finalization"),  // 종료 합의 대기 중 (DUO만)
    COMPLETED("completed"),
    TERMINATED("terminated"),

    // 기존 운영 데이터 호환만 (deprecated, 신규 진입 X)
    WAITING_B("waiting_b"),
    B_JOINED("b_joined"),
    IN_MEDIATION("in_mediation"),
    SOLO_MODE("solo_mode");

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
