package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingSimulation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 마케팅 시뮬레이션 저장소 (JPA/MariaDB)
 * 소스 스토리를 기반으로 한 AI 중재 시뮬레이션 실행 기록 조회
 */
@Repository
public interface MarketingSimulationRepository extends JpaRepository<MarketingSimulation, Long> {

    /**
     * 특정 상태의 시뮬레이션 페이징 조회
     */
    Page<MarketingSimulation> findByStatus(MarketingSimulation.Status status, Pageable pageable);

    /**
     * 특정 소스 스토리의 시뮬레이션 페이징 조회
     */
    Page<MarketingSimulation> findBySourceStoryId(Long storyId, Pageable pageable);

    /**
     * 특정 세션의 시뮬레이션 단건 조회
     */
    Optional<MarketingSimulation> findBySessionId(String sessionId);
}
