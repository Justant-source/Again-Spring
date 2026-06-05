package com.againspring.service.admin;

import com.againspring.domain.audit.AdminAuditLog;
import com.againspring.repository.audit.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 관리자 감사 로그 서비스
 * 관리자 작업을 기록하고 저장
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {

    private final AdminAuditLogRepository adminAuditLogRepository;

    /**
     * 관리자 작업 로깅
     *
     * @param actorUserId 작업 수행자 (관리자) ID
     * @param action      작업명 (예: POST_UPDATE, POST_DELETE)
     * @param targetType  대상 타입 (예: POST, COMMENT)
     * @param targetId    대상 ID
     * @param beforeJson  변경 전 JSON (null 가능)
     * @param afterJson   변경 후 JSON (null 가능)
     * @param ip          요청 IP 주소
     */
    public void log(
            String actorUserId,
            String action,
            String targetType,
            String targetId,
            String beforeJson,
            String afterJson,
            String ip) {

        try {
            AdminAuditLog auditLog = AdminAuditLog.builder()
                    .actorUserId(actorUserId)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .beforeJson(beforeJson)
                    .afterJson(afterJson)
                    .ip(ip)
                    .build();

            adminAuditLogRepository.save(auditLog);
            log.debug("Admin audit logged: actor={}, action={}, target={}:{}",
                      actorUserId, action, targetType, targetId);
        } catch (Exception e) {
            log.warn("Failed to log admin audit: action={}, error={}",
                     action, e.getMessage(), e);
            // 로깅 실패가 요청을 실패하게 하지 않음
        }
    }
}
