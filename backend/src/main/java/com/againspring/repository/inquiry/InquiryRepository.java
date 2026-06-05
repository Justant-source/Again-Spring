package com.againspring.repository.inquiry;

import com.againspring.domain.inquiry.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 문의 저장소 (V64)
 */
@Repository
public interface InquiryRepository extends JpaRepository<Inquiry, String> {

    /**
     * 상태별 문의 조회 (최신순)
     */
    Page<Inquiry> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * 사용자별 문의 조회 (최신순)
     */
    Page<Inquiry> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * 상태 및 사용자별 문의 조회
     */
    Page<Inquiry> findByStatusAndUserIdOrderByCreatedAtDesc(
            String status, String userId, Pageable pageable);

    /**
     * 상태별 문의 개수 조회
     */
    long countByStatus(String status);
}
