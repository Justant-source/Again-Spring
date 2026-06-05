package com.againspring.repository.ai;

import com.againspring.domain.ai.AiGlobalRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiGlobalRuleRepository extends JpaRepository<AiGlobalRule, Long> {

    /** 활성 규칙 전체 (scope 필터) */
    @Query("SELECT r FROM AiGlobalRule r WHERE r.active = true AND (r.scope = :scope OR r.scope = 'ALL') ORDER BY r.createdAt ASC")
    List<AiGlobalRule> findActiveByScope(@Param("scope") String scope);

    /** 관리 화면용 — 전체 목록 (최신순) */
    Page<AiGlobalRule> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 활성 여부 필터링 */
    Page<AiGlobalRule> findByActiveOrderByCreatedAtDesc(boolean active, Pageable pageable);
}
