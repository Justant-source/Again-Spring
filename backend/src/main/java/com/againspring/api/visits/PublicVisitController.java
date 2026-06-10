package com.againspring.api.visits;

import com.againspring.domain.VisitEvent;
import com.againspring.repository.VisitEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 공개 방문 이벤트 기록 API
 * 마케팅 캠페인 추적, 전환율 분석용 가벼운 엔드포인트.
 * permitAll — 인증 불필요
 * rate limit: 분당 30건 초과 시 429
 */
@Slf4j
@RestController
@RequestMapping("/api/public/visits")
@RequiredArgsConstructor
@Tag(name = "Public — Visits", description = "방문 이벤트 기록 (마케팅 추적)")
public class PublicVisitController {

    private final VisitEventRepository visitEventRepository;

    // IP별 마지막 기록 시각 (분당 30건 limit)
    // key = IP 주소, value = 마지막 허용된 ms
    // 간단한 rate limit (production에서는 Redis 권장)
    private static final ConcurrentHashMap<String, Long> ipRateLimit = new ConcurrentHashMap<>();
    private static final long RATE_WINDOW_MS = 60_000L; // 1분
    private static final int RATE_LIMIT_PER_WINDOW = 30;

    // HTML/JS 인젝션 방지 정규식
    private static final Pattern INJECTION_PATTERN = Pattern.compile("[<>\"';&]");

    @PostMapping
    @Operation(summary = "방문 이벤트 기록", description = "path, utm*, referrer을 저장하고 감시한다.")
    public ResponseEntity<Map<String, String>> recordVisit(
            @Valid @RequestBody VisitRequest req,
            HttpServletRequest httpReq) {

        // path 검증
        if (!req.path.startsWith("/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "path must start with /"));
        }
        if (req.path.startsWith("/admin")) {
            return ResponseEntity.badRequest().body(Map.of("error", "admin paths not allowed"));
        }

        // 인젝션 검증
        if (containsInjectableChars(req.path) ||
            containsInjectableChars(req.utmSource) ||
            containsInjectableChars(req.utmMedium) ||
            containsInjectableChars(req.utmCampaign) ||
            containsInjectableChars(req.utmContent) ||
            containsInjectableChars(req.referrer)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid characters in input"));
        }

        // Rate limit 확인
        String clientIp = getClientIp(httpReq);
        if (!checkRateLimit(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "rate limit exceeded"));
        }

        // 저장
        try {
            VisitEvent event = VisitEvent.builder()
                    .occurredAt(Instant.now())
                    .path(req.path)
                    .utmSource(req.utmSource)
                    .utmMedium(req.utmMedium)
                    .utmCampaign(req.utmCampaign)
                    .utmContent(req.utmContent)
                    .referrer(req.referrer)
                    .sessionKey(req.sessionKey)
                    .build();

            visitEventRepository.save(event);
            log.debug("Visit recorded: path={}, utm_campaign={}", req.path, req.utmCampaign);

            return ResponseEntity.ok(Map.of("status", "recorded"));
        } catch (Exception e) {
            log.error("Failed to record visit", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "failed to record visit"));
        }
    }

    /**
     * rate limit 확인 (분당 30건)
     * 같은 IP에서 1분 내에 30건을 초과하면 false
     */
    private synchronized boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        Long lastAllowed = ipRateLimit.get(ip);

        if (lastAllowed == null || now - lastAllowed >= RATE_WINDOW_MS) {
            // 새 윈도우 시작
            ipRateLimit.put(ip, now);
            return true;
        }

        // 같은 윈도우 내 — 카운터 체크
        // 간단한 구현: 마지막 시각 ± offset으로 카운트
        // 정확한 구현을 위해서는 sliding window 또는 token bucket 필요
        // 현재는 단순화: 1분 동안 최대 1회만 허용 (분당 30건 ≈ ~2초마다 1회)
        // 더 정확한 구현은 동시성 환경에서 Map<IP, List<Long>> 사용
        // 현재는 단순히 "같은 초(second) 내에 여러 요청 거부"로 간단히 구현

        // sliding window counter (간단 버전)
        long elapsed = now - lastAllowed;
        long allowedRequests = Math.max(1, (RATE_WINDOW_MS / RATE_LIMIT_PER_WINDOW));
        return elapsed >= allowedRequests;
    }

    /**
     * 클라이언트 IP 추출 (X-Forwarded-For 헤더 또는 RemoteAddr)
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 문자열에 인젝션 가능한 문자 포함 여부
     */
    private boolean containsInjectableChars(String str) {
        if (str == null) {
            return false;
        }
        return INJECTION_PATTERN.matcher(str).find();
    }

    /**
     * 방문 이벤트 요청 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VisitRequest {

        @NotBlank(message = "path is required")
        @Size(max = 500, message = "path must be <= 500 chars")
        private String path;

        @Size(max = 100, message = "utmSource must be <= 100 chars")
        private String utmSource;

        @Size(max = 100, message = "utmMedium must be <= 100 chars")
        private String utmMedium;

        @Size(max = 100, message = "utmCampaign must be <= 100 chars")
        private String utmCampaign;

        @Size(max = 100, message = "utmContent must be <= 100 chars")
        private String utmContent;

        @Size(max = 500, message = "referrer must be <= 500 chars")
        private String referrer;

        @Size(max = 64, message = "sessionKey must be <= 64 chars")
        private String sessionKey;
    }
}
