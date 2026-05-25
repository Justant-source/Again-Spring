package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 마케팅 콘텐츠 감사 로그 저장소 (JPA/MariaDB)
 */
@Repository
public interface MarketingAuditLogRepository extends JpaRepository<MarketingAuditLog, Long> {

    /**
     * 특정 콘텐츠의 감사 로그를 생성 시각 역순으로 조회
     */
    List<MarketingAuditLog> findByContentIdOrderByCreatedAtDesc(Long contentId);
}
