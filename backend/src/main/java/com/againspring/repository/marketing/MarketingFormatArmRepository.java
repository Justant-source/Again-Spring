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

    /** arm이 배정된 잡 수(플랫폼 무관). */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE format_arm = :armKey AND created_at >= :since
        """)
    long countAssignedSince(@Param("armKey") String armKey, @Param("since") Instant since);

    /** arm이 특정 플랫폼 타깃으로 배정된 잡 수. */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*) FROM marketing_job
        WHERE format_arm = :armKey AND created_at >= :since
        AND JSON_CONTAINS(targets, JSON_QUOTE(:platform)) = TRUE
        """)
    long countAssignedSinceForPlatform(
        @Param("armKey") String armKey,
        @Param("platform") String platform,
        @Param("since") Instant since);
}
