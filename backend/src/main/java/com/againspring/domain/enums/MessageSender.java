package com.againspring.domain.enums;

/**
 * 메시지 발신자 (V1.5 카톡식 채팅)
 */
public enum MessageSender {
    USER_A,           // A 사용자가 보낸 메시지
    USER_B,           // B 사용자가 보낸 메시지 (Duo만)
    MEDIATOR_TO_A,    // 중재자가 A에게 보낸 응답
    MEDIATOR_TO_B;    // 중재자가 B에게 보낸 응답 (Duo만)

    public boolean isUser() {
        return this == USER_A || this == USER_B;
    }

    public boolean isMediator() {
        return this == MEDIATOR_TO_A || this == MEDIATOR_TO_B;
    }

    public MessageSender mediatorCounterpart() {
        return switch (this) {
            case USER_A -> MEDIATOR_TO_A;
            case USER_B -> MEDIATOR_TO_B;
            default -> throw new IllegalStateException("Not a user sender: " + this);
        };
    }

    public MessageSender opposite() {
        return switch (this) {
            case USER_A -> USER_B;
            case USER_B -> USER_A;
            case MEDIATOR_TO_A -> MEDIATOR_TO_B;
            case MEDIATOR_TO_B -> MEDIATOR_TO_A;
        };
    }
}
