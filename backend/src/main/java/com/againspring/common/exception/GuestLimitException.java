package com.againspring.common.exception;

public class GuestLimitException extends BusinessException {

    public GuestLimitException() {
        super("GUEST_LIMIT_REACHED",
              "게스트는 3턴까지 체험 가능합니다. 회원가입 후 계속 이야기해보세요.",
              402);
    }
}
