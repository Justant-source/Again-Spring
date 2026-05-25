package com.againspring.service.marketing;

import com.againspring.api.dto.request.StoryRequest;
import com.againspring.api.dto.response.StoryResponse;
import com.againspring.api.dto.response.StorySummaryResponse;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Story management service.
 * V15.2: Create, retrieve, approve/reject marketing stories.
 * 커뮤니티 게시글은 이미 공개 텍스트이므로 별도 익명화 없이 즉시 APPROVED 처리.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Transactional
public class StoryService {

    private final MarketingSourceStoryRepository storyRepo;

    public StoryResponse create(StoryRequest req, String adminUserId) {
        MarketingSourceStory story = MarketingSourceStory.builder()
            .sourcePlatform(req.getSourcePlatform())
            .sourceUrl(req.getSourceUrl())
            .rawText(req.getRawText())
            .anonymizedText(req.getRawText())   // 커뮤니티 공개글 — 원문 그대로 사용
            .relationType(req.getRelationType())
            .status(MarketingSourceStory.Status.APPROVED)  // 즉시 시뮬레이션 가능
            .createdBy(adminUserId)
            .build();

        MarketingSourceStory saved = storyRepo.save(story);
        log.info("Story {} created: platform={}", saved.getId(), req.getSourcePlatform());
        return StoryResponse.from(saved);
    }

    public List<StorySummaryResponse> findAll(String status) {
        List<MarketingSourceStory> stories;

        if (status != null && !status.isBlank()) {
            try {
                MarketingSourceStory.Status statusEnum = MarketingSourceStory.Status.valueOf(status.toUpperCase());
                stories = storyRepo.findByStatus(statusEnum, org.springframework.data.domain.Pageable.unpaged()).getContent();
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status filter: {}", status);
                stories = storyRepo.findAll();
            }
        } else {
            stories = storyRepo.findAll();
        }

        return stories.stream()
            .map(StorySummaryResponse::from)
            .toList();
    }

    public StoryResponse findById(Long id) {
        MarketingSourceStory story = storyRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Story not found: " + id));
        return StoryResponse.from(story);
    }

    public StoryResponse approve(Long id) {
        MarketingSourceStory story = storyRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Story not found: " + id));
        story.setStatus(MarketingSourceStory.Status.APPROVED);
        MarketingSourceStory saved = storyRepo.save(story);
        return StoryResponse.from(saved);
    }

    public StoryResponse reject(Long id, String reason) {
        MarketingSourceStory story = storyRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Story not found: " + id));
        story.setStatus(MarketingSourceStory.Status.REJECTED);
        story.setBlockedReason(reason);
        MarketingSourceStory saved = storyRepo.save(story);
        return StoryResponse.from(saved);
    }

    public void delete(Long id) {
        if (!storyRepo.existsById(id)) {
            throw new EntityNotFoundException("Story not found: " + id);
        }
        storyRepo.deleteById(id);
    }
}
