package com.againspring.repository.inquiry;

import com.againspring.domain.inquiry.InquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 문의 메시지 저장소 (V64)
 */
@Repository
public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    /**
     * 문의의 모든 메시지 조회 (시간순)
     */
    List<InquiryMessage> findByInquiryIdOrderByCreatedAtAsc(String inquiryId);
}
