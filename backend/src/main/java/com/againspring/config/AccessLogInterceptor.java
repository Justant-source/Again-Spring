package com.againspring.config;

import com.againspring.service.retention.AccessLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 개인정보 접근 로그 인터셉터
 * 민감한 엔드포인트 접근을 감시
 */
@Component
@RequiredArgsConstructor
public class AccessLogInterceptor implements HandlerInterceptor {

    private final AccessLogService accessLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 감시할 엔드포인트
        boolean isSensitive = path.contains("/api/users/me") ||
            path.contains("/api/reports/") ||
            path.contains("/api/sessions/me");

        if (isSensitive && "GET".equalsIgnoreCase(method)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String userId = auth.getName();
                accessLogService.logAccess(userId, path);
            }
        }

        return true;
    }

}
