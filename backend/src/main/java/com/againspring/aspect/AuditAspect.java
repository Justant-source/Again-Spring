package com.againspring.aspect;

import com.againspring.annotation.Auditable;
import com.againspring.service.admin.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 관리자 감사 로깅 Aspect
 * @Auditable 애노테이션이 있는 메서드 실행 후 감사 로그를 자동 기록
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AdminAuditService adminAuditService;

    /**
     * @Auditable 메서드 실행 후 감사 로그 기록
     */
    @AfterReturning("@annotation(auditable)")
    public void auditMethodCall(JoinPoint joinPoint, Auditable auditable) {
        try {
            // 1. 현재 사용자 ID 추출
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                log.debug("No authenticated user for audit");
                return;
            }
            String actorUserId = auth.getName();

            // 2. 대상 ID SpEL 표현식 평가 (예: "#postId" -> 메서드 인자에서 postId 추출)
            String targetId = null;
            if (!auditable.targetId().isEmpty()) {
                targetId = evaluateSpelExpression(auditable.targetId(), joinPoint);
            }

            // 3. 요청 IP 추출 (X-Forwarded-For 우선, fallback: remoteAddr)
            String ip = extractClientIp();

            // 4. 감사 로그 기록
            adminAuditService.log(
                    actorUserId,
                    auditable.action(),
                    auditable.targetType(),
                    targetId,
                    null,  // beforeJson (선택사항)
                    null,  // afterJson (선택사항)
                    ip
            );

        } catch (Exception e) {
            log.warn("AuditAspect processing failed: {}", e.getMessage(), e);
            // Aspect 실패가 비즈니스 로직을 실패하게 하지 않음
        }
    }

    /**
     * SpEL 표현식 평가 (단순 구현: #paramName 형식)
     * 예: "#postId" -> 메서드 인자 위치로부터 값 추출
     * 주의: 간단한 구현이므로, 같은 타입의 파라미터가 여러 개면 정확하지 않을 수 있음
     */
    private String evaluateSpelExpression(String expression, JoinPoint joinPoint) {
        if (expression.isEmpty() || !expression.startsWith("#")) {
            return expression;
        }

        String paramName = expression.substring(1);  // "#"를 제거한 파라미터명

        try {
            // 메서드의 파라미터 이름과 값 매핑 (reflection 없이 간단히)
            Object[] args = joinPoint.getArgs();

            // joinPoint.getArgs()로부터 파라미터명을 추출하는 것은 어려우므로,
            // 단순 케이스만 지원: #id, #postId, #commentId 등
            // 더 정교한 구현은 MethodSignature와 Parameter 정보 필요

            // 대부분의 관리자 API는 PathVariable로 단일 ID를 전달하므로,
            // 첫 번째 String 타입 인자를 ID로 간주
            if (args.length > 0) {
                for (Object arg : args) {
                    if (arg instanceof String && !arg.equals("unknown")) {
                        return arg.toString();
                    }
                    if (arg instanceof Long) {
                        return arg.toString();
                    }
                }
            }

            log.debug("Could not resolve SpEL expression: {}", expression);
        } catch (Exception e) {
            log.debug("Failed to evaluate SpEL expression: {} ({})", expression, e.getMessage());
        }

        return null;
    }

    /**
     * 클라이언트 IP 주소 추출
     * X-Forwarded-For 헤더 우선, fallback: remoteAddr
     */
    private String extractClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();

                // X-Forwarded-For 헤더 (프록시/로드밸런서 환경)
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isEmpty()) {
                    return forwarded.split(",")[0].trim();
                }

                // 직접 연결 IP
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Failed to extract client IP: {}", e.getMessage());
        }
        return "unknown";
    }
}
