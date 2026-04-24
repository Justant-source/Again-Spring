package com.againspring.repository;

import com.againspring.domain.relationship.TemperatureHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 관계 온도 이력 저장소 (JPA/MariaDB)
 */
@Repository
public interface TemperatureHistoryRepository extends JpaRepository<TemperatureHistory, Long> {

    /**
     * 두 사용자 간의 온도 이력 조회 (기록순)
     */
    List<TemperatureHistory> findByUserIdAndRelatedUserIdOrderByRecordedAtAsc(
            String userId, String relatedUserId);

    /**
     * 사용자의 모든 온도 이력 조회 (최신순)
     */
    List<TemperatureHistory> findByUserIdOrderByRecordedAtDesc(String userId);
}
