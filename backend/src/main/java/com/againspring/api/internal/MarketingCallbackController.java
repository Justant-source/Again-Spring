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
        String expected = "Bearer " + asmProperties.getCallbackToken();
        if (authHeader == null || !constantTimeEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                authHeader.getBytes(StandardCharsets.UTF_8))) {
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
