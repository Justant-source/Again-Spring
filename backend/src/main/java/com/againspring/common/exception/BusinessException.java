package com.againspring.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final String message;
    private final int httpStatus;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
        this.httpStatus = 400; // default
    }

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
        this.httpStatus = 400;
    }

}
