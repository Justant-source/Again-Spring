package com.againspring.repository.marketing;

import com.againspring.domain.marketing.MarketingFormatArmEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MarketingFormatArmRepository extends JpaRepository<MarketingFormatArmEntity, String> {

    List<MarketingFormatArmEntity> findAllByOrderByArmKeyAsc();

    List<MarketingFormatArmEntity> findByEnabledTrueOrderByArmKeyAsc();

    /** arm이 배정된 자동 게시 잡 수(플랫폼 무관). 테스트 탭 잡(auto_publish=0)은 배정 균등화를 왜곡하므로 세지 않는다. */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE format_arm = :armKey AND created_at >= :since AND auto_publish = 1
        """)
    long countAssignedSince(@Param("armKey") String armKey, @Param("since") Instant since);

    /** arm이 특정 플랫폼 타깃으로 배정된 잡 수. */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE format_arm = :armKey AND created_at >= :since AND auto_publish = 1
        AND JSON_CONTAINS(targets, JSON_QUOTE(:platform)) = TRUE
        """)
    long countAssignedSinceForPlatform(
        @Param("armKey") String armKey,
        @Param("platform") String platform,
        @Param("since") Instant since);
}
