package com.againspring.service.admin;

import com.againspring.api.dto.response.CrisisMessageResponse;
import com.againspring.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin 위기 모니터링 — 최근 위기 메시지 메타데이터 조회.
 * 정책: 메시지 본문 노출 금지 (CrisisMessageResponse가 content를 포함하지 않음).
 */
@Service
@RequiredArgsConstructor
public class CrisisMonitoringService {

    private static final int MAX_LIMIT = 100;

    private final MessageRepository messageRepository;

    @Transactional(readOnly = true)
    public List<CrisisMessageResponse> getRecent(int limit) {
        int safe = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return messageRepository.findRecentCrisisMessages(PageRequest.of(0, safe))
                .stream()
                .map(CrisisMessageResponse::from)
                .toList();
    }
}
