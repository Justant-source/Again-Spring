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
     * Telegram "재구동" 버튼 승인 경로: ASM이 기존 callback 토큰으로 단일 잡 재구동을 위임 호출한다.
     * 어드민 JWT 없이 이 내부 채널로만 접근 가능하며, 호출자는 remoteJobId(ULID)로 신원 증명해야 한다.
     * remoteJobId 불일치 시 409로 거부하여 dev id 재사용 위험을 차단한다.
     */
    @PostMapping("/redrive")
    public ResponseEntity<Map<String, Object>> redrive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestBody InternalRedriveRequest req) {
        if (!isAuthorized(authHeader)) {
            log.debug("Marketing internal redrive rejected: invalid or missing token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // ① 신원 증명 필수: remoteJobId 필드 확인
        if (req == null || req.remoteJobId() == null || req.remoteJobId().trim().isEmpty()) {
            log.info("[REDRIVE_REJECT] reason=NO_IDENTITY jobId={}", req != null ? req.jobId() : "?");
            return ResponseEntity.badRequest().build();
        }

        // ② 잡 존재 확인
        var jobOpt = marketingJobService.findJobById(req.jobId());
        if (jobOpt.isEmpty()) {
            log.info("[REDRIVE_REJECT] reason=JOB_NOT_FOUND jobId={}", req.jobId());
            return ResponseEntity.notFound().build();
        }

        // ③ 신원 일치 확인: 저장된 remoteJobId와 비교
        var job = jobOpt.get();
        if (!req.remoteJobId().equals(job.getRemoteJobId())) {
            log.info("[REDRIVE_REJECT] reason=IDENTITY_MISMATCH jobId={} expected={} got={}",
                    req.jobId(), job.getRemoteJobId(), req.remoteJobId());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        // ④ 검증 통과: 재구동 진행
        log.info("[REDRIVE_INTERNAL] jobId={} remoteJobId={} skipExisting={} via=asm-callback-token",
                req.jobId(), req.remoteJobId(), req.skipExisting());
        List<Map<String, Object>> results = marketingJobService.redriveJobs(
                List.of(req.jobId()), req.skipExisting(), "asm:telegram-redrive");
        return ResponseEntity.ok(Map.of(
                "requested", 1,
                "results", results));
    }

    public record InternalRedriveRequest(long jobId, String remoteJobId, boolean skipExisting) {
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
