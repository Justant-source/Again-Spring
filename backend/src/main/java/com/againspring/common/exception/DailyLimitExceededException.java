package com.againspring.common.exception;

public class DailyLimitExceededException extends BusinessException {

    public DailyLimitExceededException() {
        super("DAILY_LIMIT_EXCEEDED",
              "오늘은 5세션을 모두 사용하셨어요. 내일 자정 후 다시 만나요!",
              429);
    }
}
