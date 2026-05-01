package com.againspring.service.report;

import com.againspring.domain.Message;
import com.againspring.domain.Report;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.MessageSender;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.MessageRepository;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.prompt.ChatPromptAssembler;
import com.againspring.service.prompt.UserProfileFragment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ReportGenerationService (V1.5 카톡식)
 * Async report generation using Claude Sonnet for duo/solo reports.
 * Calls LLMProvider with custom model selection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final LLMProvider llmBridge;
    private final MessageRepository messageRepo;
    private final ReportRepository reportRepo;
    private final SessionRepository sessionRepo;
    private final UserRepository userRepo;
    private final ReportResponseParser parser;
    private final MetaphorSelector metaphorSelector;
    private final RatioEnforcer ratioEnforcer;
    private final PromptLoader promptLoader;
    private final ChatPromptAssembler chatPromptAssembler;
    private final UserProfileFragment profileFragment;

    @Value("${llm.claude-code.report-model:claude-sonnet-4-6}")
    private String reportModel;

    private final ExecutorService reportExecutor = Executors.newFixedThreadPool(2);

    /**
     * Solo report: single user perspective only.
     * Triggered when user completes (≥3 messages).
     */
    @Async
    public void generateSoloReport(String sessionId) {
        log.info("Generating solo report for session {}", sessionId);
        Instant startTime = Instant.now();

        try {
            Session session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            // Determine perspective (solo could be either A or B)
            MessageSender perspective = (session.getUserBId() != null) ? MessageSender.USER_B : MessageSender.USER_A;

            List<Message> messages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);

            // Build prompt for solo report
            String prompt = buildSoloReportPrompt(session, messages, perspective);

            // Invoke LLM via ClaudeCodeBridge with Sonnet model
            String llmResponse;
            try {
                llmResponse = invokeWithModel(prompt, reportModel, sessionId);
            } catch (Exception e) {
                log.error("LLM call failed for solo report {}", sessionId, e);
                llmResponse = "{}"; // Fallback empty JSON
            }

            // Parse response
            ReportResponseParser.ParsedReport parsed = parser.parse(llmResponse);

            // Build and save report
            Report report = buildSoloReport(session, parsed, perspective);
            reportRepo.save(report);

            // 세션에 reportId 연결
            sessionRepo.findById(sessionId).ifPresent(s -> {
                s.setReportId(report.getId());
                sessionRepo.save(s);
            });

            long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Solo report generated for session {} in {}ms", sessionId, duration);

        } catch (Exception e) {
            log.error("Failed to generate solo report for session {}", sessionId, e);
            try {
                reportRepo.save(Report.builder()
                    .id("rep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                    .sessionId(sessionId)
                    .soloMode(true)
                    .llmProvider("error-fallback")
                    .createdAt(Instant.now())
                    .build());
            } catch (Exception saveFailure) {
                log.error("Even fallback solo report save failed for session {}", sessionId, saveFailure);
            }
        }
    }

    /**
     * Duo report: both perspectives (A and B) analyzed separately then combined.
     * Triggered when both agree to finalize.
     */
    @Async
    public void generateDuoReport(String sessionId) {
        log.info("Generating duo report for session {}", sessionId);
        Instant startTime = Instant.now();

        try {
            Session session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            List<Message> allMessages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);

            // Build both prompts upfront
            String promptA = buildDuoReportPrompt(session, allMessages, MessageSender.USER_A);
            String promptB = buildDuoReportPrompt(session, allMessages, MessageSender.USER_B);

            // Generate A and B perspective reports in parallel
            CompletableFuture<String> futureA = CompletableFuture.supplyAsync(() -> {
                try {
                    return invokeWithModel(promptA, reportModel, sessionId);
                } catch (Exception e) {
                    log.error("LLM call failed for duo report A perspective {}", sessionId, e);
                    return "{}";
                }
            }, reportExecutor);
            CompletableFuture<String> futureB = CompletableFuture.supplyAsync(() -> {
                try {
                    return invokeWithModel(promptB, reportModel, sessionId);
                } catch (Exception e) {
                    log.error("LLM call failed for duo report B perspective {}", sessionId, e);
                    return "{}";
                }
            }, reportExecutor);

            String responseA = futureA.join();
            String responseB = futureB.join();

            // Parse both responses
            ReportResponseParser.ParsedReport parsedA = parser.parse(responseA);
            ReportResponseParser.ParsedReport parsedB = parser.parse(responseB);

            // Enforce ratio clipping
            RatioEnforcer.Enforced ratio = ratioEnforcer.enforce(
                parsedA.getContributionRatio() != null ? parsedA.getContributionRatio().a : 50,
                parsedB.getContributionRatio() != null ? parsedB.getContributionRatio().b : 50,
                session.getConflictType()
            );

            // Build combined report
            Report report = buildDuoReport(session, parsedA, parsedB, ratio);
            reportRepo.save(report);

            // 세션에 reportId 연결
            sessionRepo.findById(sessionId).ifPresent(s -> {
                s.setReportId(report.getId());
                sessionRepo.save(s);
            });

            long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Duo report generated for session {} in {}ms", sessionId, duration);

        } catch (Exception e) {
            log.error("Failed to generate duo report for session {}", sessionId, e);
            try {
                reportRepo.save(Report.builder()
                    .id("rep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                    .sessionId(sessionId)
                    .soloMode(false)
                    .llmProvider("error-fallback")
                    .createdAt(Instant.now())
                    .build());
            } catch (Exception saveFailure) {
                log.error("Even fallback duo report save failed for session {}", sessionId, saveFailure);
            }
        }
    }

    /**
     * Build solo report prompt.
     */
    private String buildSoloReportPrompt(Session session, List<Message> messages, MessageSender perspective) throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append(promptLoader.get("system.md")).append("\n\n");
        sb.append(promptLoader.get("gottman/four_horsemen.md")).append("\n\n");
        sb.append(promptLoader.get("nvc/four_steps.md")).append("\n\n");
        String userId = (perspective == MessageSender.USER_A) ? session.getUserAId() : session.getUserBId();
        String soloProfile = profileFragment.render(loadUserSafely(userId));
        if (!soloProfile.isEmpty()) {
            sb.append(soloProfile).append("\n");
        }
        sb.append(promptLoader.get("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
        sb.append(promptLoader.get("chat/solo_report.md")).append("\n\n");

        sb.append("<conversation_history>\n");
        for (var msg : messages) {
            // Skip system-only notices (join/finalize prompts — not conversation content)
            if (Boolean.TRUE.equals(msg.getIsPartnerJoinNotice())
                    || Boolean.TRUE.equals(msg.getIsFinalizeSuggestion())) {
                continue;
            }
            sb.append("[").append(formatSender(msg.getSender())).append("] ")
              .append(msg.getContent()).append("\n");
        }
        sb.append("</conversation_history>\n\n");

        if (session.getConflictType() != null) {
            sb.append("<conflict_type>").append(session.getConflictType()).append("</conflict_type>\n\n");
        }

        sb.append(promptLoader.get("chat/_response_instructions.md"));

        return sb.toString();
    }

    /**
     * Build duo report prompt (perspective-specific).
     */
    private String buildDuoReportPrompt(Session session, List<Message> messages, MessageSender perspective) throws Exception {
        StringBuilder sb = new StringBuilder();

        sb.append(promptLoader.get("system.md")).append("\n\n");
        sb.append(promptLoader.get("gottman/four_horsemen.md")).append("\n\n");
        sb.append(promptLoader.get("nvc/four_steps.md")).append("\n\n");
        String profileA = profileFragment.render(loadUserSafely(session.getUserAId()), MessageSender.USER_A);
        String profileB = profileFragment.render(loadUserSafely(session.getUserBId()), MessageSender.USER_B);
        if (!profileA.isEmpty()) sb.append(profileA);
        if (!profileB.isEmpty()) sb.append(profileB);
        if (!profileA.isEmpty() || !profileB.isEmpty()) sb.append("\n");
        sb.append(promptLoader.get("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
        sb.append(promptLoader.get("chat/duo_report.md")).append("\n\n");

        sb.append("<conversation_history>\n");
        for (var msg : messages) {
            sb.append("[").append(formatSender(msg.getSender())).append("] ")
              .append(msg.getContent()).append("\n");
        }
        sb.append("</conversation_history>\n\n");

        sb.append("<perspective>").append(perspective == MessageSender.USER_A ? "A" : "B").append("</perspective>\n\n");

        if (session.getConflictType() != null) {
            sb.append("<conflict_type>").append(session.getConflictType()).append("</conflict_type>\n\n");
        }

        sb.append(promptLoader.get("chat/_response_instructions.md"));

        return sb.toString();
    }

    private User loadUserSafely(String userId) {
        if (userId == null) return null;
        try {
            return userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load user {} for report enrichment: {}", userId, e.getMessage());
            return null;
        }
    }

    private String invokeWithModel(String prompt, String model, String sessionId) throws Exception {
        try {
            return llmBridge.invoke(prompt, model);
        } catch (Exception e) {
            log.error("LLM invocation failed for session {}: {}", sessionId, e.getMessage());
            throw e;
        }
    }

    /**
     * Build Report entity for solo mode.
     */
    private Report buildSoloReport(Session session, ReportResponseParser.ParsedReport parsed, MessageSender perspective) {
        String reportId = "rep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        Report.Participant participantA = Report.Participant.builder()
            .userId(session.getCreatedByUserId())
            .nicknameSnapshot(null)  // TODO: Fetch from User entity if needed
            .build();

        Report.Participant participantB = null;
        if (session.getUserBId() != null) {
            participantB = Report.Participant.builder()
                .userId(session.getUserBId())
                .nicknameSnapshot(null)  // TODO: Fetch from User entity if needed
                .build();
        }

        Report.ContributionRatio ratio = null;
        if (parsed.getContributionRatio() != null) {
            ratio = Report.ContributionRatio.builder()
                .a(parsed.getContributionRatio().a)
                .b(parsed.getContributionRatio().b)
                .rationale(parsed.getContributionRatio().rationale)
                .build();
        }

        Report.NeedsMap needsMap = null;
        if (parsed.getNeedsMap() != null) {
            needsMap = Report.NeedsMap.builder()
                .axisX(parsed.getNeedsMap().axisX)
                .axisXLabel(parsed.getNeedsMap().axisXLabel)
                .axisY(parsed.getNeedsMap().axisY)
                .axisYLabel(parsed.getNeedsMap().axisYLabel)
                .interpretation(parsed.getNeedsMap().interpretation)
                .build();
        }

        Report.FourHorsemenAnalysis horsemen = null;
        if (parsed.getFourHorsemen() != null) {
            horsemen = Report.FourHorsemenAnalysis.builder()
                .criticism(mapHorsemenItem(parsed.getFourHorsemen().criticism))
                .contempt(mapHorsemenItem(parsed.getFourHorsemen().contempt))
                .defensiveness(mapHorsemenItem(parsed.getFourHorsemen().defensiveness))
                .stonewalling(mapHorsemenItem(parsed.getFourHorsemen().stonewalling))
                .build();
        }

        Report.NVCScripts nvcScripts = null;
        if (parsed.getNvcScripts() != null) {
            nvcScripts = Report.NVCScripts.builder()
                .aToB(mapNvcScript(parsed.getNvcScripts().aToB))
                .bToA(mapNvcScript(parsed.getNvcScripts().bToA))
                .build();
        }

        return Report.builder()
            .id(reportId)
            .sessionId(session.getId())
            .participantA(participantA)
            .participantB(participantB)
            .conflictType(session.getConflictType())
            .soloMode(true)
            .contributionRatio(ratio)
            .needsMap(needsMap)
            .fourHorsemen(horsemen)
            .nvcScripts(nvcScripts)
            .repairSuggestions(parsed.getRepairSuggestions())
            .llmProvider("claude-sonnet")
            .llmCallCount(1)
            .aPatternFeedback(parsed.getPatternFeedback())
            .suggestedApproach(parsed.getSuggestedApproach())
            .inviteAgainCTA(parsed.getInviteAgainCta())
            .createdAt(Instant.now())
            .build();
    }

    /**
     * Build Report entity for duo mode.
     */
    private Report buildDuoReport(Session session, ReportResponseParser.ParsedReport parsedA,
                                  ReportResponseParser.ParsedReport parsedB, RatioEnforcer.Enforced ratio) {
        String reportId = "rep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        Report.Participant participantA = Report.Participant.builder()
            .userId(session.getCreatedByUserId())
            .nicknameSnapshot(null)  // TODO: Fetch from User entity if needed
            .build();

        Report.Participant participantB = Report.Participant.builder()
            .userId(session.getUserBId())
            .nicknameSnapshot(null)  // TODO: Fetch from User entity if needed
            .build();

        Report.ContributionRatio ratioObj = Report.ContributionRatio.builder()
            .a(ratio.a())
            .b(ratio.b())
            .clippedFrom(ratio.wasClipped() ? "llm" : null)
            .build();

        // Use A's parsed data as primary (could merge both, but for simplicity use A)
        Report.NeedsMap needsMap = null;
        if (parsedA.getNeedsMap() != null) {
            needsMap = Report.NeedsMap.builder()
                .axisX(parsedA.getNeedsMap().axisX)
                .axisXLabel(parsedA.getNeedsMap().axisXLabel)
                .axisY(parsedA.getNeedsMap().axisY)
                .axisYLabel(parsedA.getNeedsMap().axisYLabel)
                .interpretation(parsedA.getNeedsMap().interpretation)
                .build();
        }

        Report.FourHorsemenAnalysis horsemen = null;
        if (parsedA.getFourHorsemen() != null) {
            horsemen = Report.FourHorsemenAnalysis.builder()
                .criticism(mapHorsemenItem(parsedA.getFourHorsemen().criticism))
                .contempt(mapHorsemenItem(parsedA.getFourHorsemen().contempt))
                .defensiveness(mapHorsemenItem(parsedA.getFourHorsemen().defensiveness))
                .stonewalling(mapHorsemenItem(parsedA.getFourHorsemen().stonewalling))
                .build();
        }

        Report.NVCScripts nvcScripts = null;
        if (parsedA.getNvcScripts() != null) {
            nvcScripts = Report.NVCScripts.builder()
                .aToB(mapNvcScript(parsedA.getNvcScripts().aToB))
                .bToA(mapNvcScript(parsedA.getNvcScripts().bToA))
                .build();
        }

        return Report.builder()
            .id(reportId)
            .sessionId(session.getId())
            .participantA(participantA)
            .participantB(participantB)
            .conflictType(session.getConflictType())
            .soloMode(false)
            .contributionRatio(ratioObj)
            .needsMap(needsMap)
            .fourHorsemen(horsemen)
            .nvcScripts(nvcScripts)
            .repairSuggestions(parsedA.getRepairSuggestions())
            .llmProvider("claude-sonnet")
            .llmCallCount(2)  // A + B perspectives
            .aPatternFeedback(parsedA.getPatternFeedback())
            .suggestedApproach(parsedA.getSuggestedApproach())
            .createdAt(Instant.now())
            .build();
    }

    private Report.NVCScripts.NVCScript mapNvcScript(ReportResponseParser.ParsedReport.NVCScript src) {
        if (src == null) return null;
        return Report.NVCScripts.NVCScript.builder()
            .observation(src.observation)
            .feeling(src.feeling)
            .need(src.need)
            .request(src.request)
            .build();
    }

    private Report.FourHorsemenAnalysis.HorsemenItem mapHorsemenItem(ReportResponseParser.ParsedReport.HorsemenItem src) {
        if (src == null) return null;
        return Report.FourHorsemenAnalysis.HorsemenItem.builder()
            .detected(src.detected)
            .intensity(src.intensity)
            .examples(src.examples)
            .build();
    }

    private String formatSender(MessageSender s) {
        return switch (s) {
            case USER_A -> "사용자 A";
            case USER_B -> "사용자 B";
            case MEDIATOR_TO_A -> "중재자→A";
            case MEDIATOR_TO_B -> "중재자→B";
        };
    }
}
