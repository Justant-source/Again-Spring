package com.againspring.common.exception;

import com.againspring.marketing.AsmUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        // 4xx는 예상된 클라이언트 오류 — admin 로그 노이즈 방지 (DEBUG)
        if (ex.getHttpStatus() < 500) {
            log.debug("Business exception: code={}, message={}, status={}",
                    ex.getCode(), ex.getMessage(), ex.getHttpStatus());
        } else {
            log.warn("Business exception: code={}, message={}, status={}",
                    ex.getCode(), ex.getMessage(), ex.getHttpStatus());
        }
        Map<String, Object> response = buildErrorResponse(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        log.debug("Validation exception: {}", ex.getMessage());
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
        Map<String, Object> response = buildErrorResponse("VALIDATION_ERROR", message);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.debug("Illegal state: {}", ex.getMessage());
        Map<String, Object> response = buildErrorResponse("INVALID_STATE", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.debug("Illegal argument: {}", ex.getMessage());
        String code = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "INVALID_ARGUMENT";
        Map<String, Object> response = buildErrorResponse(code, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /** ResponseStatusException은 HTTP 상태를 그대로 반환 — Exception.class보다 먼저 매칭. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.debug("ResponseStatusException: status={}, reason={}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> response = buildErrorResponse(
            ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString(),
            ex.getMessage()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    @ExceptionHandler(AsmUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleAsmUnavailable(AsmUnavailableException ex) {
        log.warn("ASM unavailable: {}", ex.getMessage());
        Map<String, Object> response = buildErrorResponse("ASM_UNAVAILABLE", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    /**
     * 클라이언트가 응답 전에 연결을 끊은 경우 (Broken pipe / ClientAbort).
     * 서버 장애가 아니므로 ERROR로 올리지 않는다.
     */
    @ExceptionHandler({
            AsyncRequestNotUsableException.class,
            org.apache.catalina.connector.ClientAbortException.class
    })
    public ResponseEntity<Void> handleClientAbort(Exception ex, HttpServletRequest request) {
        log.debug("Client aborted request {} {}: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        if (isClientAbort(ex)) {
            log.debug("Client aborted request {} {}: {}",
                    request.getMethod(), request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        Map<String, Object> response = buildErrorResponse("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    static boolean isClientAbort(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            String name = t.getClass().getName();
            String msg = t.getMessage() != null ? t.getMessage() : "";
            if (name.contains("ClientAbortException")
                    || name.contains("AsyncRequestNotUsableException")
                    || msg.contains("Broken pipe")
                    || msg.contains("Connection reset by peer")
                    || (t instanceof IOException && msg.contains("failed to write"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private Map<String, Object> buildErrorResponse(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("timestamp", Instant.now());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", error);
        return response;
    }

}
