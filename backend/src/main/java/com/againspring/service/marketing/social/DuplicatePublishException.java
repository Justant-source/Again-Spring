package com.againspring.service.marketing.social;

/**
 * 중복 발행 시도 예외
 * 이미 발행된 플랫폼에 재발행 시도 또는 발행 비활성화 시 발생
 */
public class DuplicatePublishException extends RuntimeException {

    public DuplicatePublishException(String message) {
        super(message);
    }

    public DuplicatePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
