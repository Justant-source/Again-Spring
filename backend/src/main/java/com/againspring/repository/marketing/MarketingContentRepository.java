package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 마케팅 콘텐츠 저장소 (JPA/MariaDB)
 * 시뮬레이션 결과로부터 생성된 플랫폼별 마케팅 콘텐츠 조회
 */
@Repository
public interface MarketingContentRepository extends JpaRepository<MarketingContent, Long> {

    /**
     * 특정 시뮬레이션과 플랫폼의 콘텐츠 단건 조회
     */
    Optional<MarketingContent> findBySimulationIdAndPlatform(
            Long simulationId, MarketingContent.Platform platform);

    /**
     * 특정 시뮬레이션의 모든 콘텐츠 조회
     */
    List<MarketingContent> findBySimulationId(Long simulationId);

    /**
     * 특정 상태의 콘텐츠 페이징 조회
     */
    Page<MarketingContent> findByStatus(MarketingContent.Status status, Pageable pageable);

    /**
     * 특정 플랫폼과 상태의 콘텐츠 페이징 조회
     */
    Page<MarketingContent> findByPlatformAndStatus(
            MarketingContent.Platform platform, MarketingContent.Status status, Pageable pageable);
}
