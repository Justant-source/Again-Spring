package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingAuditLog;
import com.againspring.domain.marketing.MarketingContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.againspring.repository.marketing.MarketingAuditLogRepository;
import com.againspring.repository.marketing.MarketingContentRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 마케팅 콘텐츠 승인 워크플로우 서비스
 * 콘텐츠 승인/거부 및 감사 로그 관리
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@Transactional
public class ApprovalWorkflowService {

    private final MarketingContentRepository contentRepository;
    private final MarketingAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ApprovalWorkflowService(
            MarketingContentRepository contentRepository,
            MarketingAuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.contentRepository = contentRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 콘텐츠 승인
     */
    public MarketingContent approve(Long contentId, String actorUserId) {
        MarketingContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new EntityNotFoundException("콘텐츠를 찾을 수 없습니다: " + contentId));

        content.setStatus(MarketingContent.Status.APPROVED);
        MarketingContent saved = contentRepository.save(content);

        // 감사 로그 기록
        MarketingAuditLog auditLog = MarketingAuditLog.builder()
                .contentId(contentId)
                .action("APPROVED")
                .actorUserId(actorUserId)
                .payloadJson("{}")
                .build();
        auditLogRepository.save(auditLog);

        log.info("마케팅 콘텐츠 승인: contentId={}, actorUserId={}", contentId, actorUserId);
        return saved;
    }

    /**
     * 콘텐츠 거부
     */
    public MarketingContent reject(Long contentId, String reason, String actorUserId) {
        MarketingContent content = contentRepository.findById(contentId)
                .orElseThrow(() -> new EntityNotFoundException("콘텐츠를 찾을 수 없습니다: " + contentId));

        content.setStatus(MarketingContent.Status.REJECTED);
        MarketingContent saved = contentRepository.save(content);

        // 감사 로그 기록
        Map<String, String> payload = new HashMap<>();
        payload.put("reason", reason);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize audit log payload", e);
            payloadJson = "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}";
        }

        MarketingAuditLog auditLog = MarketingAuditLog.builder()
                .contentId(contentId)
                .action("REJECTED")
                .actorUserId(actorUserId)
                .payloadJson(payloadJson)
                .build();
        auditLogRepository.save(auditLog);

        log.info("마케팅 콘텐츠 거부: contentId={}, actorUserId={}, reason={}", contentId, actorUserId, reason);
        return saved;
    }

    /**
     * 콘텐츠의 감사 로그 조회
     */
    @Transactional(readOnly = true)
    public List<MarketingAuditLog> getAuditLogs(Long contentId) {
        return auditLogRepository.findByContentIdOrderByCreatedAtDesc(contentId);
    }
}
