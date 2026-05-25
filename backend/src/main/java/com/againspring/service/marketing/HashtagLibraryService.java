package com.againspring.service.marketing;

import com.againspring.api.dto.request.HashtagRequest;
import com.againspring.api.dto.response.HashtagResponse;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingHashtag;
import com.againspring.repository.marketing.MarketingHashtagRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class HashtagLibraryService {

    private final MarketingHashtagRepository hashtagRepo;

    public List<HashtagResponse> findAll(String platformStr) {
        if (platformStr != null && !platformStr.isBlank()) {
            MarketingContent.Platform platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
            return hashtagRepo.findByPlatformOrderByUsageCountDesc(platform).stream()
                    .map(HashtagResponse::from).collect(Collectors.toList());
        }
        return hashtagRepo.findAllByOrderByUsageCountDesc().stream()
                .map(HashtagResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public HashtagResponse create(HashtagRequest req) {
        MarketingContent.Platform platform = MarketingContent.Platform.valueOf(req.getPlatform().toUpperCase());
        try {
            MarketingHashtag hashtag = MarketingHashtag.builder()
                    .platform(platform)
                    .tag(req.getTag().startsWith("#") ? req.getTag().substring(1) : req.getTag())
                    .category(req.getCategory())
                    .usageCount(0)
                    .build();
            MarketingHashtag saved = hashtagRepo.save(hashtag);
            log.info("Created hashtag: id={}, platform={}, tag={}", saved.getId(), platform, saved.getTag());
            return HashtagResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Hashtag already exists for this platform: " + req.getTag());
        }
    }

    @Transactional
    public void recordUsage(String platformStr, String tag) {
        MarketingContent.Platform platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
        String cleanTag = tag.startsWith("#") ? tag.substring(1) : tag;
        hashtagRepo.findByPlatformAndTag(platform, cleanTag).ifPresent(h -> {
            h.setUsageCount(h.getUsageCount() + 1);
            h.setLastUsedAt(Instant.now());
            hashtagRepo.save(h);
        });
    }

    @Transactional
    public void delete(Long id) {
        if (!hashtagRepo.existsById(id)) throw new EntityNotFoundException("Hashtag not found: " + id);
        hashtagRepo.deleteById(id);
        log.info("Deleted hashtag: id={}", id);
    }
}
