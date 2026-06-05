package com.againspring.repository.community;

import com.againspring.domain.community.CommunityReport;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 커뮤니티 신고 저장소 (V17 커뮤니티)
 */
@Repository
public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {

    /**
     * 상태별 신고 조회 (생성순 역순, 페이지네이션)
     */
    Page<CommunityReport> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * 상태별 신고 개수 조회 (배지 폴링용)
     */
    long countByStatus(String status);
}
