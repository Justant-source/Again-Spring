package com.againspring.marketing;

/**
 * Exception thrown when ASM service is unavailable or returns an error
 */
public class AsmUnavailableException extends RuntimeException {

    public AsmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public AsmUnavailableException(String message) {
        super(message);
    }
}
