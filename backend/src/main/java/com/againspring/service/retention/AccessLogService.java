package com.againspring.service.retention;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 개인정보 접근 로그 서비스
 * 사용자가 민감한 엔드포인트 접근 시 감시 목적으로 기록
 * 대상 엔드포인트: GET /me, GET /reports/{id}, GET /sessions/me
 *
 * TODO Phase 13: 전용 MongoDB 컬렉션에 저장 (현재는 logger 사용)
 */
@Slf4j
@Service
public class AccessLogService {

    private static final org.slf4j.Logger auditLogger = org.slf4j.LoggerFactory.getLogger("com.againspring.retention.audit");

    /**
     * 개인정보 접근 기록
     */
    public void logAccess(String userId, String path) {
        auditLogger.info("User {} accessed sensitive endpoint: {}", userId, path);
    }

    /**
     * 특정 리소스 접근 기록
     */
    public void logResourceAccess(String userId, String resourceType, String resourceId) {
        auditLogger.info("User {} accessed {} resource: {}", userId, resourceType, resourceId);
    }

}
