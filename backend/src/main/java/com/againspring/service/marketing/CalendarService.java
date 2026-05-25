package com.againspring.service.marketing;

import com.againspring.api.dto.response.CalendarItemResponse;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.repository.marketing.MarketingContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CalendarService {

    private final MarketingContentRepository contentRepo;

    public List<CalendarItemResponse> getItems(Instant from, Instant to) {
        List<MarketingContent> scheduled = contentRepo.findAll().stream()
                .filter(c -> c.getScheduledAt() != null &&
                        c.getScheduledAt().isAfter(from) &&
                        c.getScheduledAt().isBefore(to))
                .collect(Collectors.toList());

        List<MarketingContent> published = contentRepo.findAll().stream()
                .filter(c -> c.getPublishedAt() != null &&
                        c.getPublishedAt().isAfter(from) &&
                        c.getPublishedAt().isBefore(to))
                .collect(Collectors.toList());

        List<MarketingContent> combined = new ArrayList<>(scheduled);
        for (MarketingContent p : published) {
            if (combined.stream().noneMatch(s -> s.getId().equals(p.getId()))) {
                combined.add(p);
            }
        }

        return combined.stream()
                .sorted(Comparator.comparing(c -> c.getScheduledAt() != null ? c.getScheduledAt() : c.getPublishedAt()))
                .map(c -> CalendarItemResponse.builder()
                        .id(c.getId())
                        .platform(c.getPlatform().toString())
                        .status(c.getStatus().toString())
                        .scheduledAt(c.getScheduledAt())
                        .publishedAt(c.getPublishedAt())
                        .title(c.getTitle())
                        .build())
                .collect(Collectors.toList());
    }
}
