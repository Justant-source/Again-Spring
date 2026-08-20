package com.againspring.api.internal;

import com.againspring.marketing.AsmProperties;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.dto.JobCallbackPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Callback receiver for ASM (Again-Spring-Marketing) service
 */
@RestController
@RequestMapping("/api/internal/marketing")
@RequiredArgsConstructor
@Slf4j
public class MarketingCallbackController {
    private final MarketingJobService marketingJobService;
    private final AsmProperties asmProperties;

    @PostMapping("/callback")
    public ResponseEntity<Void> receiveCallback(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody JobCallbackPayload payload) {
        if (!isAuthorized(authHeader)) {
            log.debug("Marketing callback rejected: invalid or missing token (job={})", payload.getJobId());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            marketingJobService.applyCallback(payload);
        } catch (Exception e) {
            log.error("Error processing marketing callback for job {}: {}", payload.getJobId(), e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Telegram "재구동" 버튼 승인 경로: ASM이 기존 callback 토큰으로 재구동을 위임 호출한다.
     * 어드민 JWT 없이 이 내부 채널로만 접근 가능하며, 대상 잡 id 명시가 필수(필터 미지원).
     */
    @PostMapping("/redrive")
    public ResponseEntity<Map<String, Object>> redrive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody InternalRedriveRequest req) {
        if (!isAuthorized(authHeader)) {
            log.debug("Marketing internal redrive rejected: invalid or missing token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (req == null || req.jobIds() == null || req.jobIds().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("[REDRIVE_INTERNAL] jobIds={} skipExisting={} via=asm-callback-token",
                req.jobIds(), req.skipExisting());
        List<Map<String, Object>> results = marketingJobService.redriveJobs(
                req.jobIds(), req.skipExisting(), "asm:telegram-redrive");
        return ResponseEntity.ok(Map.of(
                "requested", req.jobIds().size(),
                "results", results));
    }

    public record InternalRedriveRequest(List<Long> jobIds, boolean skipExisting) {
    }

    private boolean isAuthorized(String authHeader) {
        String expected = "Bearer " + asmProperties.getCallbackToken();
        return authHeader != null && constantTimeEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                authHeader.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        byte result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
