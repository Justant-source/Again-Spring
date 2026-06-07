package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 마케팅 콘텐츠 저장소 (JPA/MariaDB)
 * 커뮤니티 게시글로부터 생성된 플랫폼별 마케팅 콘텐츠 조회
 */
@Repository
public interface MarketingContentRepository extends JpaRepository<MarketingContent, Long> {

    /**
     * 특정 커뮤니티 게시글의 모든 콘텐츠 조회
     */
    List<MarketingContent> findBySourcePostId(String sourcePostId);

    /**
     * 특정 상태의 콘텐츠 페이징 조회
     */
    Page<MarketingContent> findByStatus(MarketingContent.Status status, Pageable pageable);

    /**
     * 특정 플랫폼과 상태의 콘텐츠 페이징 조회
     */
    Page<MarketingContent> findByPlatformAndStatus(
            MarketingContent.Platform platform, MarketingContent.Status status, Pageable pageable);

    /**
     * 특정 기간 내 예약된 콘텐츠 조회
     */
    List<MarketingContent> findByScheduledAtBetween(Instant from, Instant to);

    /**
     * 특정 기간 내 발행된 콘텐츠 조회
     */
    List<MarketingContent> findByPublishedAtBetween(Instant from, Instant to);

    /**
     * 특정 시간 이후의 예약 콘텐츠 조회 (시간순 정렬)
     */
    List<MarketingContent> findByScheduledAtAfterOrderByScheduledAtAsc(Instant cutoff);
}
