package com.againspring.service.marketing;

import com.againspring.domain.Report;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
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
 * ContentService와 분리된 빈으로 @Async 프록시가 올바르게 적용되도록 함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class ContentGenerationExecutor {

    private final MarketingContentRepository contentRepo;
    private final MarketingSourceStoryRepository storyRepo;
    private final ReportRepository reportRepository;
    private final PlatformContentRouter router;
    private final MarketingCopyGuard copyGuard;
    private final ImageCompositionStrategyRegistry imageStrategyRegistry;
    private final ObjectMapper objectMapper;

    @Value("${app.features.marketing.image-dir:/tmp/marketing-images}")
    private String imageDir;

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

            GenerationOutput output = router.generate(platform, simulationSummary, relationType);

            boolean hasViolations = output.bodyText() != null && copyGuard.hasViolations(output.bodyText());

            // Image composition per platform strategy
            Report report = simulation.getSessionId() != null
                    ? reportRepository.findBySessionId(simulation.getSessionId()).orElse(null)
                    : null;

            String finalImagePaths = composeAndSaveImages(platform, output, simulation, report, contentId);

            MarketingContent content = contentRepo.findById(contentId).orElseThrow();
            content.setBodyText(output.bodyText());
            if (output.hashtags() != null) content.setHashtags(output.hashtags());
            content.setStatus(hasViolations ? MarketingContent.Status.REVIEW : MarketingContent.Status.DRAFT);
            content.setSafetyCheckJson(buildSafetyJson(hasViolations));
            if (finalImagePaths != null) content.setImagePaths(finalImagePaths);
            contentRepo.save(content);

            log.info("Content generation completed: id={}, platform={}, status={}, hasImages={}",
                    contentId, platform, content.getStatus(), finalImagePaths != null);
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

    private String composeAndSaveImages(
            MarketingContent.Platform platform,
            GenerationOutput output,
            MarketingSimulation simulation,
            Report report,
            Long contentId
    ) {
        return imageStrategyRegistry.find(platform).map(strategy -> {
            try {
                List<RenderedImage> images = strategy.compose(output, simulation, report, contentId, imageDir);
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

    private String buildSummary(MarketingSimulation simulation) {
        if (simulation.getSessionId() != null) {
            Report report = reportRepository.findBySessionId(simulation.getSessionId()).orElse(null);
            if (report != null && report.getCoreSummary() != null) {
                return buildSummaryFromReport(report, simulation);
            }
        }
        if (simulation.getConversationLog() != null && !simulation.getConversationLog().isBlank()) {
            return String.format("대화 기록:\n%s\n\n턴 수: %d",
                    simulation.getConversationLog(),
                    simulation.getActualTurnCount() != null ? simulation.getActualTurnCount() : 0);
        }
        return String.format("Persona A: %s\nTurns: %d",
                simulation.getPersonaA() != null ? simulation.getPersonaA() : "Unknown",
                simulation.getActualTurnCount() != null ? simulation.getActualTurnCount() : 0);
    }

    private String buildSummaryFromReport(Report report, MarketingSimulation simulation) {
        StringBuilder sb = new StringBuilder();
        if (report.getCoreSummary() != null) sb.append("핵심 요약: ").append(report.getCoreSummary()).append("\n");
        if (report.getNvcObservation() != null) sb.append("NVC 관찰: ").append(report.getNvcObservation()).append("\n");
        if (report.getNvcNeed() != null) sb.append("NVC 욕구: ").append(report.getNvcNeed()).append("\n");
        if (report.getMetaphorDisplayName() != null) sb.append("관계 메타포: ").append(report.getMetaphorDisplayName()).append("\n");
        sb.append("턴 수: ").append(simulation.getActualTurnCount() != null ? simulation.getActualTurnCount() : 0);
        return sb.toString();
    }

    private String buildSafetyJson(boolean hasViolations) {
        return String.format("{\"violations_detected\": %b, \"checked_at\": \"%s\"}",
                hasViolations, java.time.Instant.now());
    }
}
