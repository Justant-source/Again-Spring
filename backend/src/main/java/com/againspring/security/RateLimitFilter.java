package com.againspring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API Rate Limiting Filter
 * 특정 엔드포인트에 대한 요청 속도 제한
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * 인증 엔드포인트(signup/login/forgot-password/send-verification) 분당 허용 횟수.
     * prod 기본값 5 (브루트포스 방어 유지). dev/e2e는 env로 상향(예: 1000)해 다회 로그인 허용.
     */
    @Value("${security.rate-limit.auth-per-minute:5}")
    private int authPerMinute;

    private static final String SIGNUP_ENDPOINT = "/api/auth/signup";
    private static final String LOGIN_ENDPOINT = "/api/auth/login";
    private static final String FORGOT_PASSWORD_ENDPOINT = "/api/auth/forgot-password";
    private static final String SEND_VERIFICATION_ENDPOINT = "/api/auth/send-verification";
    private static final String TURNS_ENDPOINT = "/api/sessions/";
    private static final String FEEDBACKS_ENDPOINT = "/api/feedbacks";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = getClientIp(request);
        String requestPath = request.getRequestURI();
        String method = request.getMethod();

        // Rate limit 적용 대상 확인
        int limit = -1;
        if ("POST".equals(method)) {
            if (isSignupOrLogin(requestPath)) {
                limit = authPerMinute; // 기본 5/분 (prod), dev/e2e는 env로 상향
            } else if (isForgotPassword(requestPath)) {
                limit = authPerMinute;
            } else if (isSendVerification(requestPath)) {
                limit = authPerMinute;
            } else if (isCreateTurn(requestPath)) {
                limit = 30; // 30 requests per minute
            } else if (isFeedbacks(requestPath)) {
                limit = 5; // 5 per hour (버킷을 1시간으로 별도 처리)
            }
        }

        if (limit == -1) {
            // No rate limit for this endpoint
            filterChain.doFilter(request, response);
            return;
        }

        // 버킷 생성/조회
        String bucketKey = clientIp + ":" + getEndpointCategory(requestPath);
        final int finalLimit = limit;
        final boolean isHourlyBucket = isFeedbacks(requestPath);
        Bucket bucket = cache.computeIfAbsent(bucketKey,
                k -> isHourlyBucket ? createHourlyBucket(finalLimit) : createNewBucket(finalLimit));

        // 토큰 소비 시도
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for IP: {}, endpoint: {}", clientIp, getEndpointCategory(requestPath));
            response.setStatus(429); // Too Many Requests
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorResponse = new HashMap<>();
            Map<String, Object> error = new HashMap<>();
            error.put("code", "RATE_LIMITED");
            error.put("message", "요청이 너무 잦아요. 잠시 후 다시 시도해주세요");
            errorResponse.put("error", error);

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 새 버킷 생성 (분 단위)
     */
    private Bucket createNewBucket(int tokensPerMinute) {
        Bandwidth limit = Bandwidth.classic(tokensPerMinute, Refill.intervally(tokensPerMinute, Duration.ofMinutes(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * 시간 단위 버킷 생성 (피드백 전용)
     */
    private Bucket createHourlyBucket(int tokensPerHour) {
        Bandwidth limit = Bandwidth.classic(tokensPerHour, Refill.intervally(tokensPerHour, Duration.ofHours(1)));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Signup/Login 엔드포인트 확인
     */
    private boolean isSignupOrLogin(String path) {
        return path.equals(SIGNUP_ENDPOINT) || path.equals(LOGIN_ENDPOINT);
    }

    /**
     * 비밀번호 재설정 엔드포인트 확인
     */
    private boolean isForgotPassword(String path) {
        return path.equals(FORGOT_PASSWORD_ENDPOINT);
    }

    /**
     * 이메일 인증 코드 발송 엔드포인트 확인
     */
    private boolean isSendVerification(String path) {
        return path.equals(SEND_VERIFICATION_ENDPOINT);
    }

    /**
     * Turn 생성 엔드포인트 확인
     */
    private boolean isCreateTurn(String path) {
        return path.matches("^/api/sessions/[^/]+/turns$");
    }

    /**
     * 피드백 제출 엔드포인트 확인
     */
    private boolean isFeedbacks(String path) {
        return path.equals(FEEDBACKS_ENDPOINT);
    }

    /**
     * 엔드포인트 카테고리 추출
     */
    private String getEndpointCategory(String path) {
        if (isSignupOrLogin(path)) {
            return path;
        } else if (isForgotPassword(path)) {
            return FORGOT_PASSWORD_ENDPOINT;
        } else if (isSendVerification(path)) {
            return SEND_VERIFICATION_ENDPOINT;
        } else if (isCreateTurn(path)) {
            return "TURNS";
        } else if (isFeedbacks(path)) {
            return FEEDBACKS_ENDPOINT;
        }
        return path;
    }
}
