package com.againspring.repository;

import com.againspring.domain.VisitEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * 방문 이벤트 저장소
 */
@Repository
public interface VisitEventRepository extends JpaRepository<VisitEvent, Long> {

    /** 같은 utm_campaign으로 since 이후 기록된 해당 분류 행이 있는가(스캐너 근접 판정). */
    boolean existsByVisitorClassAndUtmCampaignAndOccurredAtGreaterThanEqual(
        String visitorClass, String utmCampaign, Instant since);

    /** utm이 없는 경우: 같은 path의 utm 없는 해당 분류 행. */
    boolean existsByVisitorClassAndPathAndUtmCampaignIsNullAndOccurredAtGreaterThanEqual(
        String visitorClass, String path, Instant since);
}
