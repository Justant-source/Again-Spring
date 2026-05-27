package com.againspring.service.marketing.image;

import com.againspring.domain.Message;
import com.againspring.domain.Report;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.repository.MessageRepository;
import com.againspring.service.marketing.ImageRenderClient;
import com.againspring.service.marketing.content.GenerationOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Image composition strategy for Naver Blog.
 * Renders up to 3 images: chat preview, report summary, quote card.
 * Replaces <!-- IMG:xxx --> markers in the markdown body text.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
public class NaverImageStrategy implements ImageCompositionStrategy {

    private final ImageRenderClient renderClient;
    private final MessageRepository messageRepository;
    private final KeyMomentSelector keyMomentSelector;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.NAVER_BLOG;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RenderedImage> compose(
            GenerationOutput output,
            MarketingSimulation sim,
            Report report,
            Long contentId,
            String imageDir
    ) throws IOException {
        Path dir = Paths.get(imageDir);
        Files.createDirectories(dir);

        Map<String, Object> payload = output.structuredPayload();
        List<Map<String, Object>> imageSlots = payload != null
                ? (List<Map<String, Object>>) payload.get("imageSlots")
                : null;

        List<RenderedImage> results = new ArrayList<>();

        if (imageSlots == null || imageSlots.isEmpty()) {
            log.warn("NaverImageStrategy: no imageSlots in payload for contentId={}", contentId);
            return results;
        }

        for (int i = 0; i < imageSlots.size(); i++) {
            Map<String, Object> slot = imageSlots.get(i);
            String kind = (String) slot.getOrDefault("kind", "");
            String slotMarker = (String) slot.getOrDefault("slot", "IMG:slot_" + i);
            String idx = String.format("%02d", i + 1);

            byte[] png = switch (kind) {
                case "chat" -> renderChatImage(sim, contentId);
                case "report-needs" -> renderReportImage(report, "needs");
                case "report-ratio" -> renderReportImage(report, "ratio");
                case "report-combined" -> renderReportImage(report, "combined");
                case "quote" -> {
                    String quoteText = (String) slot.get("quoteText");
                    if (quoteText == null && report != null)
                        quoteText = report.getMetaphorDisplayName();
                    yield renderClient.renderQuote(quoteText, "", "다시봄", "warm");
                }
                default -> null;
            };

            if (png == null || png.length == 0) {
                log.warn("NaverImageStrategy: {} render returned empty for contentId={}", kind, contentId);
                continue;
            }

            String filename = "naver_" + contentId + "_" + idx + "_" + kind.replace("-", "_") + ".png";
            Files.write(dir.resolve(filename), png);

            String roleKey = switch (kind) {
                case "chat" -> "CHAT_PREVIEW";
                case "report-needs" -> "REPORT_NEEDS";
                case "report-ratio" -> "REPORT_RATIO";
                case "report-combined" -> "REPORT_COMBINED";
                case "quote" -> "QUOTE_CARD";
                default -> kind.toUpperCase();
            };
            String alt = switch (kind) {
                case "chat" -> "AI와의 대화 장면";
                case "report-needs" -> "NeedsMap 다이어그램";
                case "report-ratio" -> "화해 기여도 그래프";
                case "report-combined" -> "리포트 요약";
                case "quote" -> "메타포 인용 카드";
                default -> filename;
            };

            results.add(new RenderedImage(filename, roleKey, slotMarker, alt, i + 1));
            log.info("Naver image saved: {}/{}", imageDir, filename);
        }

        return results;
    }

    private byte[] renderChatImage(MarketingSimulation sim, Long contentId) {
        if (sim.getSessionId() == null) return null;
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sim.getSessionId());
        if (messages.isEmpty()) return null;
        List<Message> keyMoments = keyMomentSelector.select(messages);
        return renderClient.renderChatPreview(
                keyMomentSelector.toRendererPayload(keyMoments), "다시봄", "AI 갈등 중재", 0);
    }

    private byte[] renderReportImage(Report report, String mode) {
        if (report == null) return null;
        Map<String, Object> reportData = buildReportData(report);
        return renderClient.renderReportSummary(reportData, mode);
    }

    private Map<String, Object> buildReportData(Report report) {
        Map<String, Object> data = new HashMap<>();

        if (report.getNeedsMap() != null) {
            Map<String, Object> nm = new HashMap<>();
            // NeedsMap inner fields depend on Report.NeedsMap inner record
            nm.put("labelA", "A님");
            nm.put("labelB", "B님");
            data.put("needsMap", nm);
        }

        if (report.getContributionRatio() != null) {
            Map<String, Object> cr = new HashMap<>();
            // ContributionRatio inner fields accessed via reflection-safe toString
            data.put("contributionRatio", cr);
        }

        if (report.getMetaphorDisplayName() != null) {
            data.put("metaphor", report.getMetaphorDisplayName());
        }

        return data;
    }
}
