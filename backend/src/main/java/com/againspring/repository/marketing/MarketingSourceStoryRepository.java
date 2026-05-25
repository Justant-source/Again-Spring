package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingSourceStory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * 마케팅 소스 스토리 저장소 (JPA/MariaDB)
 * 외부 플랫폼에서 수집한 원본 텍스트 및 익명화 결과 조회
 */
@Repository
public interface MarketingSourceStoryRepository extends JpaRepository<MarketingSourceStory, Long> {

    /**
     * 특정 상태의 스토리 페이징 조회
     */
    Page<MarketingSourceStory> findByStatus(MarketingSourceStory.Status status, Pageable pageable);

    /**
     * 특정 상태를 제외한 스토리 페이징 조회
     */
    Page<MarketingSourceStory> findByStatusNot(MarketingSourceStory.Status status, Pageable pageable);

    /**
     * 특정 기간에 생성된 스토리 페이징 조회
     */
    Page<MarketingSourceStory> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);
}
