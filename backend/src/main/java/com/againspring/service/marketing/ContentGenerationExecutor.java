package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.safety.MarketingCopyGuard;
import com.againspring.service.marketing.content.GenerationOutput;
import com.againspring.service.marketing.content.PlatformContentRouter;
import com.againspring.service.marketing.image.ImageCompositionStrategy;
import com.againspring.service.marketing.image.ImageCompositionStrategyRegistry;
import com.againspring.service.marketing.image.RenderedImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 콘텐츠 비동기 생성 실행 빈.
 * 커뮤니티 게시글을 소스로 플랫폼별 마케팅 카피를 생성한다.
 * ContentService와 분리된 빈으로 @Async 프록시가 올바르게 적용되도록 함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class ContentGenerationExecutor {

    private final MarketingContentRepository contentRepo;
    private final PlatformContentRouter router;
    private final MarketingCopyGuard copyGuard;
    private final ImageCompositionStrategyRegistry imageStrategyRegistry;
    private final CommunityPostMarketingReader postReader;
    private final ObjectMapper objectMapper;

    @Value("${app.features.marketing.image-dir:/tmp/marketing-images}")
    private String imageDir;

    @Async("marketingExecutor")
    public void executeFromPost(Long contentId, String postId, MarketingContent.Platform platform) {
        try {
            // 커뮤니티 게시글 로드 + 요약 생성
            CommunityPostMarketingReader.PostMarketingData data = postReader.load(postId);
            String sourceContent = postReader.buildSourceContent(data);
            String relationType = data.relationType();

            GenerationOutput output = router.generate(platform, sourceContent, relationType);

            boolean hasViolations = output.bodyText() != null && copyGuard.hasViolations(output.bodyText());

            String finalImagePaths = composeAndSaveImages(platform, output, relationType, contentId);

            MarketingContent content = contentRepo.findById(contentId).orElseThrow();
            content.setBodyText(output.bodyText());
            if (output.hashtags() != null) content.setHashtags(output.hashtags());
            content.setStatus(hasViolations ? MarketingContent.Status.REVIEW : MarketingContent.Status.DRAFT);
            content.setSafetyCheckJson(buildSafetyJson(hasViolations));
            if (finalImagePaths != null) content.setImagePaths(finalImagePaths);
            contentRepo.save(content);

            log.info("Content generation completed: id={}, platform={}, status={}, postId={}, hasImages={}",
                    contentId, platform, content.getStatus(), postId, finalImagePaths != null);
        } catch (Exception e) {
            log.error("Content generation failed: id={}, postId={}", contentId, postId, e);
            contentRepo.findById(contentId).ifPresent(c -> {
                c.setStatus(MarketingContent.Status.REJECTED);
                c.setSafetyCheckJson(String.format(
                        "{\"rejected\": true, \"reason\": \"%s\", \"rejected_at\": \"%s\"}",
                        e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Unknown error",
                        java.time.Instant.now()));
                contentRepo.save(c);
            });
        }
    }

    private String composeAndSaveImages(
            MarketingContent.Platform platform,
            GenerationOutput output,
            String relationType,
            Long contentId
    ) {
        return imageStrategyRegistry.find(platform).map(strategy -> {
            try {
                List<RenderedImage> images = strategy.compose(output, relationType, contentId, imageDir);
                if (images.isEmpty()) return null;
                return objectMapper.writeValueAsString(images.stream()
                        .map(img -> java.util.Map.of(
                                "filename", img.filename(),
                                "role", img.role(),
                                "slot", img.slot(),
                                "alt", img.alt(),
                                "order", img.order()))
                        .toList());
            } catch (Exception e) {
                log.warn("Image composition/serialization failed for contentId={}: {}", contentId, e.getMessage());
                return null;
            }
        }).orElse(null);
    }

    private String buildSafetyJson(boolean hasViolations) {
        return String.format("{\"violations_detected\": %b, \"checked_at\": \"%s\"}",
                hasViolations, java.time.Instant.now());
    }
}
