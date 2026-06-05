package com.againspring.repository.audit;

import com.againspring.domain.audit.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 관리자 감사 로그 저장소 (V63)
 */
@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    /**
     * 행위자 ID 또는 액션으로 감사 로그 조회 (페이지네이션)
     */
    Page<AdminAuditLog> findByActorUserIdOrActionContaining(
            String actorUserId, String action, Pageable pageable);

    /**
     * 행위자별 감사 로그 조회
     */
    Page<AdminAuditLog> findByActorUserIdOrderByCreatedAtDesc(
            String actorUserId, Pageable pageable);

    /**
     * 액션별 감사 로그 조회
     */
    Page<AdminAuditLog> findByActionOrderByCreatedAtDesc(
            String action, Pageable pageable);
}
