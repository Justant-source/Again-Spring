package com.againspring.api.visits;

import com.againspring.domain.VisitEvent;
import com.againspring.repository.VisitEventRepository;
import com.againspring.service.acquisition.VisitorClassifier;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
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
    private final VisitorClassifier visitorClassifier;

    // IP별 최근 요청 시각들 (슬라이딩 윈도우 카운터)
    //
    // 2026-08-29: 이전 구현은 "윈도우 시작 시각"만 저장하고 그 뒤 2초(=60s/30) 안의
    // 요청을 전부 거부했다. UTM/외부 referrer가 있을 때만 기록하던 시절엔 드러나지
    // 않았지만, 모든 페이지뷰를 기록하게 되면서 홈 → 사연처럼 빠르게 이동하는 정상
    // 사용자의 두 번째 방문이 429로 유실됐다(e2e에서 실측). 진짜 슬라이딩 윈도우로 바꾼다.
    private static final ConcurrentHashMap<String, Deque<Long>> ipHits = new ConcurrentHashMap<>();
    private static final long RATE_WINDOW_MS = 60_000L; // 1분
    private static final int RATE_LIMIT_PER_WINDOW = 30;
    /** 맵이 무한히 자라지 않도록 하는 상한. 넘으면 오래된 항목부터 비운다. */
    private static final int MAX_TRACKED_IPS = 10_000;

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

        String userAgent = httpReq.getHeader("User-Agent");

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
                    .visitorKey(req.visitorKey)
                    .userAgent(truncate(userAgent, 300))
                    // 봇도 저장하되 표시해 둔다. 지우면 오탐을 영영 검증할 수 없다.
                    .bot(visitorClassifier.isBot(userAgent))
                    .country(visitorClassifier.country(httpReq))
                    .deviceType(visitorClassifier.deviceType(userAgent))
                    .userId(currentUserId())
                    .build();

            visitEventRepository.save(event);
            log.debug("Visit recorded: path={}, utm_campaign={}, bot={}",
                    req.path, req.utmCampaign, event.isBot());

            return ResponseEntity.ok(Map.of("status", "recorded"));
        } catch (Exception e) {
            log.error("Failed to record visit", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "failed to record visit"));
        }
    }

    /**
     * rate limit 확인 — IP당 60초 슬라이딩 윈도우로 최대 30건.
     *
     * <p>계측은 사용자 행동을 막지 않아야 하지만, 공개 엔드포인트라 무제한으로 열어둘 수도
     * 없다. 윈도우 안의 실제 요청 수를 세므로 "빠르게 3페이지를 본 사람"은 통과하고
     * "초당 수십 건을 쏘는 스크립트"는 막힌다.
     */
    private boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        long cutoff = now - RATE_WINDOW_MS;

        if (ipHits.size() > MAX_TRACKED_IPS) {
            // 만료된 항목을 정리한다. 이전 구현은 맵을 영원히 비우지 않아 서서히 샜다.
            ipHits.entrySet().removeIf(e -> {
                Deque<Long> hits = e.getValue();
                synchronized (hits) {
                    while (!hits.isEmpty() && hits.peekFirst() < cutoff) {
                        hits.pollFirst();
                    }
                    return hits.isEmpty();
                }
            });
        }

        Deque<Long> hits = ipHits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < cutoff) {
                hits.pollFirst();
            }
            if (hits.size() >= RATE_LIMIT_PER_WINDOW) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }

    /** 테스트 전용 — 슬라이딩 윈도우 상태 초기화. */
    static void resetRateLimitState() {
        ipHits.clear();
    }

    /**
     * 인증 컨텍스트의 사용자 id. 이 엔드포인트는 permitAll이지만 토큰이 함께 오면
     * 방문 → 투표 → 가입을 한 사람으로 이을 수 있다. 익명이면 null.
     */
    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
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

        @Size(max = 64, message = "visitorKey must be <= 64 chars")
        private String visitorKey;

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
