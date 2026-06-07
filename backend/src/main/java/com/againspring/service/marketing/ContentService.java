package com.againspring.service.marketing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.againspring.api.dto.request.ContentFromTemplateRequest;
import com.againspring.api.dto.request.PublishRequest;
import com.againspring.api.dto.request.ScheduleRequest;
import com.againspring.api.dto.response.ContentResponse;
import com.againspring.api.dto.response.ContentSummaryResponse;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingContentTemplate;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.MarketingContentTemplateRepository;
import com.againspring.safety.MarketingCopyGuard;
import com.againspring.service.marketing.content.PlatformContentRouter;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing marketing content generation and lifecycle.
 * 소스: 커뮤니티 게시글(Post) — 외부사연/시뮬레이션 제거.
 * Async generation is delegated to ContentGenerationExecutor to preserve @Async proxy.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private static final List<String> DEFAULT_PLATFORMS = List.of("x", "instagram", "naver_blog");

    private final MarketingContentRepository contentRepo;
    private final MarketingCopyGuard copyGuard;
    private final ContentGenerationExecutor generationExecutor;
    private final MarketingContentTemplateRepository templateRepo;
    private final PlatformContentRouter router;
    private final CommunityPostMarketingReader postReader;

    /**
     * 커뮤니티 게시글로부터 플랫폼별 콘텐츠 비동기 생성.
     * platforms 미지정 시 x, instagram, naver_blog 3종 동시 생성.
     */
    @Transactional
    public List<ContentResponse> generateFromPost(String postId, List<String> platforms) {
        // 게시글 존재 + 공개 여부 검증
        postReader.load(postId);

        List<String> targetPlatforms = (platforms == null || platforms.isEmpty())
                ? DEFAULT_PLATFORMS
                : platforms;

        List<ContentResponse> results = new ArrayList<>();
        for (String platformStr : targetPlatforms) {
            MarketingContent.Platform platform;
            try {
                platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Skipping invalid platform: {}", platformStr);
                continue;
            }

            // GENERATING stub 저장
            MarketingContent stub = MarketingContent.builder()
                    .sourcePostId(postId)
                    .platform(platform)
                    .bodyText("")
                    .status(MarketingContent.Status.GENERATING)
                    .build();
            MarketingContent saved = contentRepo.save(stub);
            log.info("Queued content generation: id={}, postId={}, platform={}", saved.getId(), postId, platform);

            generationExecutor.executeFromPost(saved.getId(), postId, platform);
            results.add(ContentResponse.from(saved));
        }

        return results;
    }

    /**
     * List all content with optional filters.
     */
    public List<ContentSummaryResponse> findAll(String statusStr, String platformStr) {
        List<MarketingContent> contents;

        if (statusStr != null && platformStr != null) {
            MarketingContent.Status status = MarketingContent.Status.valueOf(statusStr.toUpperCase());
            MarketingContent.Platform platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
            contents = contentRepo.findByPlatformAndStatus(platform, status,
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
        } else if (statusStr != null) {
            MarketingContent.Status status = MarketingContent.Status.valueOf(statusStr.toUpperCase());
            contents = contentRepo.findByStatus(status,
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
        } else if (platformStr != null) {
            MarketingContent.Platform platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
            contents = contentRepo.findAll().stream()
                    .filter(c -> c.getPlatform() == platform)
                    .collect(Collectors.toList());
        } else {
            contents = contentRepo.findAll();
        }

        return contents.stream()
                .map(ContentSummaryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Find single content by ID.
     */
    public ContentResponse findById(Long id) {
        MarketingContent content = contentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));
        return ContentResponse.from(content);
    }

    /**
     * Update content body text.
     */
    public ContentResponse update(Long id, String bodyText) {
        MarketingContent content = contentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));

        content.setBodyText(bodyText);
        MarketingContent updated = contentRepo.save(content);
        log.info("Updated marketing content: id={}", id);

        return ContentResponse.from(updated);
    }

    /**
     * Approve content after manual review and re-run safety check.
     */
    public ContentResponse approve(Long id) {
        MarketingContent content = contentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));

        boolean hasViolations = copyGuard.hasViolations(content.getBodyText());
        if (!hasViolations) {
            content.setStatus(MarketingContent.Status.APPROVED);
            content.setSafetyCheckJson(String.format(
                    "{\"violations_detected\": false, \"checked_at\": \"%s\"}", java.time.Instant.now()));
        } else {
            content.setSafetyCheckJson(String.format(
                    "{\"violations_detected\": true, \"checked_at\": \"%s\"}", java.time.Instant.now()));
        }

        MarketingContent updated = contentRepo.save(content);
        log.info("Approved marketing content: id={}, status={}", id, updated.getStatus());

        return ContentResponse.from(updated);
    }

    /**
     * Delete content permanently.
     */
    @Transactional
    public void delete(Long id) {
        if (!contentRepo.existsById(id)) {
            throw new EntityNotFoundException("Content not found: " + id);
        }
        contentRepo.deleteById(id);
        log.info("Deleted marketing content: id={}", id);
    }

    /**
     * Reject content with reason.
     */
    public ContentResponse reject(Long id, String reason) {
        MarketingContent content = contentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));

        content.setStatus(MarketingContent.Status.REJECTED);
        content.setSafetyCheckJson(String.format(
                "{\"rejected\": true, \"reason\": \"%s\", \"rejected_at\": \"%s\"}",
                reason != null ? reason : "No reason provided", java.time.Instant.now()));

        MarketingContent updated = contentRepo.save(content);
        log.info("Rejected marketing content: id={}, reason={}", id, reason);

        return ContentResponse.from(updated);
    }

    @Transactional
    public ContentResponse schedule(Long id, ScheduleRequest request) {
        MarketingContent content = contentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));
        content.setScheduledAt(request.getScheduledAt());
        MarketingContent updated = contentRepo.save(content);
        log.info("Scheduled content: id={}, scheduledAt={}", id, request.getScheduledAt());
        return ContentResponse.from(updated);
    }

    @Transactional
    public ContentResponse publish(Long id, PublishRequest request) {
        MarketingContent content = contentRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));
        content.setPublishedAt(request != null && request.getPublishedAt() != null
                ? request.getPublishedAt()
                : java.time.Instant.now());
        if (request != null && request.getPublishedUrl() != null) {
            content.setPublishedUrl(request.getPublishedUrl());
        }
        content.setStatus(MarketingContent.Status.EXPORTED);
        MarketingContent updated = contentRepo.save(content);
        log.info("Published content: id={}, url={}", id, content.getPublishedUrl());
        return ContentResponse.from(updated);
    }

    @Transactional
    public ContentResponse generateFromTemplate(Long templateId, ContentFromTemplateRequest req) {
        MarketingContentTemplate template = templateRepo.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

        // 커뮤니티 게시글 로드 + 소스 콘텐츠 생성
        CommunityPostMarketingReader.PostMarketingData postData = postReader.load(req.getPostId());
        String sourceContent = postReader.buildSourceContent(postData);
        String relationType = postData.relationType();

        MarketingContent.Platform platform = req.getPlatform() != null
                ? MarketingContent.Platform.valueOf(req.getPlatform().toUpperCase())
                : template.getPlatform();

        String instantiatedBody = template.getBodyTemplate();
        if (req.getVariables() != null) {
            for (java.util.Map.Entry<String, String> entry : req.getVariables().entrySet()) {
                instantiatedBody = instantiatedBody.replace("${" + entry.getKey() + "}", entry.getValue());
            }
        }
        if (instantiatedBody.contains("${") || instantiatedBody.contains("{{")) {
            throw new IllegalStateException("Template has unresolved placeholders. Provide all required variable values.");
        }

        try {
            com.againspring.service.marketing.content.GenerationOutput output = router.generateWithTemplate(
                    platform,
                    sourceContent,
                    relationType,
                    instantiatedBody
            );
            String bodyText = output.bodyText() != null ? output.bodyText() : "";
            boolean hasViolations = copyGuard.hasViolations(bodyText);

            MarketingContent content = MarketingContent.builder()
                    .sourcePostId(req.getPostId())
                    .platform(platform)
                    .bodyText(bodyText)
                    .hashtags(output.hashtags())
                    .status(hasViolations ? MarketingContent.Status.REVIEW : MarketingContent.Status.DRAFT)
                    .templateId(templateId)
                    .safetyCheckJson(String.format(
                            "{\"violations_detected\": %b, \"template_id\": %d, \"checked_at\": \"%s\"}",
                            hasViolations, templateId, java.time.Instant.now()))
                    .build();

            MarketingContent saved = contentRepo.save(content);
            log.info("Generated from template: templateId={}, contentId={}, postId={}", templateId, saved.getId(), req.getPostId());
            return ContentResponse.from(saved);
        } catch (Exception e) {
            throw new RuntimeException("Content generation from template failed: " + e.getMessage(), e);
        }
    }
}
