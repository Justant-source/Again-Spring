package com.againspring.service.marketing;

import com.againspring.api.dto.request.PerformanceRequest;
import com.againspring.api.dto.response.ContentResponse;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PerformanceService {

    private final MarketingContentRepository contentRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public ContentResponse recordPerformance(Long id, PerformanceRequest req) {
        MarketingContent content = contentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Marketing content not found: " + id));

        Map<String, Object> performanceMap;
        if (content.getPerformanceJson() != null && !content.getPerformanceJson().isEmpty()) {
            try {
                performanceMap = objectMapper.readValue(content.getPerformanceJson(), Map.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse performanceJson", e);
            }
        } else {
            performanceMap = new HashMap<>();
        }

        if (req.getImpressions() != null) {
            performanceMap.put("impressions", req.getImpressions());
        }
        if (req.getLikes() != null) {
            performanceMap.put("likes", req.getLikes());
        }
        if (req.getComments() != null) {
            performanceMap.put("comments", req.getComments());
        }
        if (req.getShares() != null) {
            performanceMap.put("shares", req.getShares());
        }
        if (req.getClicks() != null) {
            performanceMap.put("clicks", req.getClicks());
        }
        if (req.getSaves() != null) {
            performanceMap.put("saves", req.getSaves());
        }
        if (req.getNote() != null) {
            performanceMap.put("note", req.getNote());
        }

        performanceMap.put("recordedAt", Instant.now().toString());

        try {
            String jsonStr = objectMapper.writeValueAsString(performanceMap);
            content.setPerformanceJson(jsonStr);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize performanceJson", e);
        }

        MarketingContent saved = contentRepo.save(content);
        return ContentResponse.from(saved);
    }
}
