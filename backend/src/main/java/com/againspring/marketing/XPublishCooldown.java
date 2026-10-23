package com.againspring.marketing;

import java.time.Duration;
import java.time.Instant;

/**
 * X가 게시를 거부(속도 제한 추정)하면 선댓글·대댓글이 같은 계정으로 계속 두드리지 않도록
 * 일정 시간 게시 시도를 모두 멈춘다. 두 서비스가 같은 게시 경로를 쓰므로 프로세스 전역 상태다.
 */
final class XPublishCooldown {

    /** ASM이 X의 게시 거부를 구분해 돌려주는 표식(publish-x.js). */
    static final String REJECTED_MARKER = "X_REJECTED";
    /** X 부팅 로고에서 멈춘 경우(속도 제한 때 몰려서 발생). */
    static final String SPLASH_MARKER = "Tweet textarea not found";

    static final Duration PAUSE = Duration.ofMinutes(12);

    private static volatile Instant until;

    private XPublishCooldown() {
    }

    static boolean isRateLimited(Throwable e) {
        return e != null && isRateLimited(e.getMessage());
    }

    static boolean isRateLimited(String message) {
        return message != null
            && (message.contains(REJECTED_MARKER) || message.contains(SPLASH_MARKER));
    }

    static void trip(Instant now) {
        until = now.plus(PAUSE);
    }

    static boolean active(Instant now) {
        Instant u = until;
        return u != null && now.isBefore(u);
    }

    static void reset() {
        until = null;
    }
}
