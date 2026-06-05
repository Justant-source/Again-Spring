package com.againspring.service.admin;

import com.againspring.api.dto.request.InquiryReplyRequest;
import com.againspring.api.dto.response.InquiryDetailResponse;
import com.againspring.domain.inquiry.Inquiry;
import com.againspring.domain.inquiry.InquiryMessage;
import com.againspring.repository.inquiry.InquiryMessageRepository;
import com.againspring.repository.inquiry.InquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminInquiryService {

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository inquiryMessageRepository;

    /**
     * 상태별 문의 목록 조회
     */
    public Page<Inquiry> listInquiries(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            // 모든 상태 조회
            return inquiryRepository.findAll(pageable);
        }
        return inquiryRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * 특정 상태의 문의 개수 조회
     */
    public long countByStatus(String status) {
        if (status == null || status.isBlank()) {
            return inquiryRepository.count();
        }
        return inquiryRepository.countByStatus(status);
    }

    /**
     * 문의 상세 조회 (모든 메시지 포함)
     */
    public InquiryDetailResponse getInquiryDetail(String id) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND"));

        List<InquiryMessage> messages = inquiryMessageRepository.findByInquiryIdOrderByCreatedAtAsc(id);
        return InquiryDetailResponse.from(inquiry, messages);
    }

    /**
     * 문의에 답변 추가 및 상태 변경
     */
    @Transactional
    public InquiryDetailResponse replyToInquiry(String inquiryId, String adminUserId, InquiryReplyRequest req) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND"));

        // 메시지 추가
        InquiryMessage message = InquiryMessage.builder()
                .inquiryId(inquiryId)
                .senderRole("ADMIN")
                .senderUserId(adminUserId)
                .body(req.getMessage())
                .build();
        inquiryMessageRepository.save(message);

        // 상태를 ANSWERED로 변경
        inquiry.setStatus("ANSWERED");
        inquiry.setAssigneeUserId(adminUserId);
        inquiryRepository.save(inquiry);

        // 업데이트된 상세 정보 반환
        List<InquiryMessage> messages = inquiryMessageRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId);
        return InquiryDetailResponse.from(inquiry, messages);
    }

    /**
     * 문의 종료
     */
    @Transactional
    public void closeInquiry(String inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND"));

        inquiry.setStatus("CLOSED");
        inquiryRepository.save(inquiry);
    }

    /**
     * 문의 삭제 (+ 모든 메시지)
     */
    @Transactional
    public void deleteInquiry(String inquiryId) {
        if (!inquiryRepository.existsById(inquiryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND");
        }

        // 메시지 먼저 삭제
        List<InquiryMessage> messages = inquiryMessageRepository.findByInquiryIdOrderByCreatedAtAsc(inquiryId);
        inquiryMessageRepository.deleteAll(messages);

        // 문의 삭제
        inquiryRepository.deleteById(inquiryId);
    }
}
