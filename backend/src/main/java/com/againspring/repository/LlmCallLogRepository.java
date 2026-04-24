package com.againspring.repository;

import com.againspring.domain.relationship.LlmCallLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * LLM 호출 로그 저장소 (JPA/MariaDB)
 */
@Repository
public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {

    /**
     * 상관관계 ID로 로그 조회 (디버깅용)
     */
    List<LlmCallLog> findByCorrelationId(String correlationId);
}
