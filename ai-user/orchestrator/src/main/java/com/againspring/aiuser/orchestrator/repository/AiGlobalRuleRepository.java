package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiGlobalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiGlobalRuleRepository extends JpaRepository<AiGlobalRule, Long> {

    /**
     * 활성 전역 금지 규칙 조회 (scope 필터).
     * 'ALL' scope는 모든 타입에 포함.
     */
    @Query("SELECT r FROM AiGlobalRule r WHERE r.active = true AND (r.scope = :scope OR r.scope = 'ALL') ORDER BY r.createdAt ASC")
    List<AiGlobalRule> findActiveByScope(@Param("scope") String scope);
}
