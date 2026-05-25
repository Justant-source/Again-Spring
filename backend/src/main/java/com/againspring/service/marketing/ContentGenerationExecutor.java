package com.againspring.service.marketing;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import com.againspring.safety.MarketingCopyGuard;
import com.againspring.service.marketing.content.PlatformContentRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 콘텐츠 비동기 생성 실행 빈.
 * ContentService와 분리된 빈으로 @Async 프록시가 올바르게 적용되도록 함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class ContentGenerationExecutor {

    private final MarketingContentRepository contentRepo;
    private final MarketingSourceStoryRepository storyRepo;
    private final PlatformContentRouter router;
    private final MarketingCopyGuard copyGuard;

    @Async("marketingExecutor")
    public void execute(Long contentId, MarketingSimulation simulation, MarketingContent.Platform platform) {
        try {
            String simulationSummary = buildSummary(simulation);

            String relationType = "general";
            if (simulation.getSourceStoryId() != null) {
                MarketingSourceStory story = storyRepo.findById(simulation.getSourceStoryId()).orElse(null);
                if (story != null && story.getRelationType() != null) {
                    relationType = story.getRelationType();
                }
            }

            String generatedText = router.generate(platform, simulationSummary, relationType);

            String sanitizedText = generatedText;
            boolean hasViolations = copyGuard.hasViolations(generatedText);
            if (hasViolations) {
                sanitizedText = copyGuard.sanitize(generatedText);
            }

            MarketingContent content = contentRepo.findById(contentId).orElseThrow();
            content.setBodyText(sanitizedText);
            content.setStatus(hasViolations ? MarketingContent.Status.REVIEW : MarketingContent.Status.DRAFT);
            content.setSafetyCheckJson(buildSafetyJson(hasViolations));
            contentRepo.save(content);

            log.info("Content generation completed: id={}, platform={}, status={}", contentId, platform, content.getStatus());
        } catch (Exception e) {
            log.error("Content generation failed: id={}", contentId, e);
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

    private String buildSummary(MarketingSimulation simulation) {
        return String.format(
                "Persona A: %s\nPersona B: %s\nTurns: %d",
                simulation.getPersonaA() != null ? simulation.getPersonaA() : "Unknown",
                simulation.getPersonaB() != null ? simulation.getPersonaB() : "Unknown",
                simulation.getActualTurnCount() != null ? simulation.getActualTurnCount() : 0);
    }

    private String buildSafetyJson(boolean hasViolations) {
        return String.format("{\"violations_detected\": %b, \"checked_at\": \"%s\"}",
                hasViolations, java.time.Instant.now());
    }
}
