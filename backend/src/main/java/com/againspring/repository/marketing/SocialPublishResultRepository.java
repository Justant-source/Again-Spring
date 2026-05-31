package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.SocialPublishResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 소셜 플랫폼별 발행 결과 저장소
 */
@Repository
public interface SocialPublishResultRepository extends JpaRepository<SocialPublishResult, Long> {

    /**
     * 콘텐츠 ID와 플랫폼별 발행 결과 조회
     */
    Optional<SocialPublishResult> findByContentIdAndPlatform(
            Long contentId, MarketingContent.Platform platform);

    /**
     * 콘텐츠 ID의 모든 발행 결과 조회
     */
    List<SocialPublishResult> findByContentId(Long contentId);
}
