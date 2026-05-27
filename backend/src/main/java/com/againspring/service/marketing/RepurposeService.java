package com.againspring.service.marketing;

import com.againspring.api.dto.response.ContentResponse;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.MarketingSimulationRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import com.againspring.safety.MarketingCopyGuard;
import com.againspring.service.marketing.content.PlatformContentRouter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class RepurposeService {

    private final MarketingContentRepository contentRepo;
    private final MarketingSimulationRepository simRepo;
    private final MarketingSourceStoryRepository storyRepo;
    private final PlatformContentRouter router;
    private final MarketingCopyGuard copyGuard;

    @Transactional
    public ContentResponse repurpose(Long sourceId, String targetPlatformStr) throws Exception {
        MarketingContent source = contentRepo.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Content not found: " + sourceId));

        if (source.getStatus() != MarketingContent.Status.APPROVED
                && source.getStatus() != MarketingContent.Status.EXPORTED) {
            throw new IllegalStateException("Only APPROVED or EXPORTED content can be repurposed. Current: " + source.getStatus());
        }

        MarketingContent.Platform targetPlatform;
        try {
            targetPlatform = MarketingContent.Platform.valueOf(targetPlatformStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid platform: " + targetPlatformStr);
        }

        if (targetPlatform == source.getPlatform()) {
            throw new IllegalArgumentException("Target platform must differ from source platform");
        }

        if (source.getRepurposeSourceId() != null) {
            throw new IllegalStateException("Cannot repurpose content that was already repurposed from another source");
        }

        boolean alreadyRepurposed = contentRepo.findAll().stream()
                .anyMatch(c -> sourceId.equals(c.getRepurposeSourceId()) && targetPlatform == c.getPlatform());
        if (alreadyRepurposed) {
            throw new IllegalStateException("A repurposed version already exists for source " + sourceId + " on " + targetPlatform);
        }

        String relationType = resolveRelationType(source);

        com.againspring.service.marketing.content.GenerationOutput output = router.generateWithTemplate(
                targetPlatform,
                "리퍼포징 원본 콘텐츠:\n" + source.getBodyText(),
                relationType,
                source.getBodyText()
        );
        String bodyText = output.bodyText() != null ? output.bodyText() : "";

        MarketingContent repurposed = MarketingContent.builder()
                .simulationId(source.getSimulationId())
                .platform(targetPlatform)
                .bodyText(bodyText)
                .hashtags(output.hashtags())
                .status(MarketingContent.Status.DRAFT)
                .parentContentId(source.getId())
                .repurposeSourceId(source.getId())
                .safetyCheckJson(String.format(
                        "{\"repurposed_from\": %d, \"checked_at\": \"%s\"}", sourceId, java.time.Instant.now()))
                .build();

        MarketingContent saved = contentRepo.save(repurposed);
        log.info("Repurposed content: sourceId={}, newId={}, targetPlatform={}", sourceId, saved.getId(), targetPlatform);
        return ContentResponse.from(saved);
    }

    private String resolveRelationType(MarketingContent source) {
        if (source.getSimulationId() == null) return "general";
        return simRepo.findById(source.getSimulationId())
                .map(sim -> {
                    if (sim.getSourceStoryId() == null) return "general";
                    return storyRepo.findById(sim.getSourceStoryId())
                            .map(story -> story.getRelationType() != null ? story.getRelationType() : "general")
                            .orElse("general");
                })
                .orElse("general");
    }
}
