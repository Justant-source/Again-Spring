package com.againspring.service.marketing;

import com.againspring.domain.Message;
import com.againspring.domain.Report;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingSourceStory;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.MarketingSourceStoryRepository;
import com.againspring.safety.MarketingCopyGuard;
import com.againspring.service.marketing.content.PlatformContentRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final MessageRepository messageRepository;
    private final PlatformContentRouter router;
    private final MarketingCopyGuard copyGuard;
    private final ImageRenderClient imageRenderClient;

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

            String generatedText = router.generate(platform, simulationSummary, relationType);

            String sanitizedText = generatedText;
            boolean hasViolations = copyGuard.hasViolations(generatedText);
            if (hasViolations) {
                sanitizedText = copyGuard.sanitize(generatedText);
            }

            // Generate chat preview screenshot if simulation has a session
            String imagePaths = null;
            if (simulation.getSessionId() != null) {
                imagePaths = generateChatScreenshot(contentId, simulation.getSessionId(), relationType);
            }

            MarketingContent content = contentRepo.findById(contentId).orElseThrow();
            content.setBodyText(sanitizedText);
            content.setStatus(hasViolations ? MarketingContent.Status.REVIEW : MarketingContent.Status.DRAFT);
            content.setSafetyCheckJson(buildSafetyJson(hasViolations));
            if (imagePaths != null) {
                content.setImagePaths(imagePaths);
            }
            contentRepo.save(content);

            log.info("Content generation completed: id={}, platform={}, status={}, hasImage={}",
                    contentId, platform, content.getStatus(), imagePaths != null);
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

    private String generateChatScreenshot(Long contentId, String sessionId, String relationType) {
        try {
            List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            if (messages.isEmpty()) return null;

            // Pass up to 5 messages to the renderer for a clean marketing shot
            List<Map<String, Object>> msgData = messages.stream()
                    .limit(5)
                    .map(m -> Map.<String, Object>of(
                            "sender", m.getSender().name(),
                            "content", m.getContent() != null ? m.getContent() : "",
                            "createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : ""
                    ))
                    .collect(Collectors.toList());

            String subtitle = relationType.isBlank() ? "AI 갈등 중재" : relationType + " · AI 갈등 중재";
            byte[] png = imageRenderClient.renderChatPreview(msgData, "다시봄", subtitle);
            if (png == null || png.length == 0) {
                log.warn("Chat screenshot renderer returned empty result for contentId={}", contentId);
                return null;
            }

            Path dir = Paths.get(imageDir);
            Files.createDirectories(dir);
            String filename = "chat_" + contentId + ".png";
            Files.write(dir.resolve(filename), png);

            log.info("Chat screenshot saved: {}/{}", imageDir, filename);
            return "[\"" + filename + "\"]";
        } catch (IOException e) {
            log.warn("Failed to save chat screenshot for contentId={}: {}", contentId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Chat screenshot generation failed for contentId={}: {}", contentId, e.getMessage());
            return null;
        }
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
        if (report.getCoreSummary() != null) {
            sb.append("핵심 요약: ").append(report.getCoreSummary()).append("\n");
        }
        if (report.getNvcObservation() != null) {
            sb.append("NVC 관찰: ").append(report.getNvcObservation()).append("\n");
        }
        if (report.getNvcNeed() != null) {
            sb.append("NVC 욕구: ").append(report.getNvcNeed()).append("\n");
        }
        if (report.getMetaphorDisplayName() != null) {
            sb.append("관계 메타포: ").append(report.getMetaphorDisplayName()).append("\n");
        }
        sb.append("턴 수: ").append(simulation.getActualTurnCount() != null ? simulation.getActualTurnCount() : 0);
        return sb.toString();
    }

    private String buildSafetyJson(boolean hasViolations) {
        return String.format("{\"violations_detected\": %b, \"checked_at\": \"%s\"}",
                hasViolations, java.time.Instant.now());
    }
}
