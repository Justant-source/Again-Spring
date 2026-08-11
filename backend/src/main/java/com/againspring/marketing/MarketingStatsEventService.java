package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingStatsEvent;
import com.againspring.repository.marketing.MarketingStatsEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Persists marketing stats activity events and serves recent timeline rows (Phase 3).
 */
@Service
@RequiredArgsConstructor
public class MarketingStatsEventService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final MarketingStatsEventRepository repository;

    @Transactional
    public MarketingStatsEvent record(String type, String platform, String payloadJson) {
        MarketingStatsEvent event = MarketingStatsEvent.builder()
            .eventType(type)
            .platform(platform)
            .payloadJson(payloadJson)
            .build();
        return repository.save(event);
    }

    @Transactional(readOnly = true)
    public List<MarketingStatsEvent> listRecent(int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit));
    }
}
