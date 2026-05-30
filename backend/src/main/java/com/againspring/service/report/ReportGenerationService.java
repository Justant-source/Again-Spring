package com.againspring.service.report;

import com.againspring.domain.Message;
import com.againspring.domain.Report;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.MessageSender;
import com.againspring.domain.enums.ReportStatus;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ReportGenerationService (V12)
 * Async report generation using Claude Sonnet.
 * V12: Fixed silent schema mismatch, removed _response_instructions.md from report prompts,
 * added retry logic, added V12 field mapping (coreSummary, fourStageFlow, metaphor,
 * nvcReflection, recommendedActions, externalResourceGuidance).
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
    private final RatioEnforcer ratioEnforcer;
    private final PromptLoader promptLoader;
    private final ChatPromptAssembler chatPromptAssembler;
    private final UserProfileFragment profileFragment;
    private final ReportContextAssembler reportContextAssembler;

    @Value("${llm.claude-code.report-model:claude-sonnet-4-6}")
    private String reportModel;

    private final ExecutorService reportExecutor = Executors.newFixedThreadPool(2);

    @Async
    public void generateSoloReport(String sessionId) {
        log.info("Generating solo report for session {}", sessionId);
        Instant startTime = Instant.now();

        try {
            Session session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            MessageSender perspective = (session.getUserBId() != null)
                ? MessageSender.USER_B : MessageSender.USER_A;
            List<Message> messages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
            String prompt = buildSoloReportPrompt(session, messages, perspective);

            String llmResponse = invokeWithRetry(prompt, sessionId, "solo");
            if (llmResponse == null) {
                saveFailedReport(sessionId, true);
                return;
            }

            ReportResponseParser.ParsedReport parsed = parser.parse(llmResponse);
            if (!parsed.hasV12Content()) {
                log.error("LLM returned incomplete V12 content for solo report {}", sessionId);
                // Try once more
                llmResponse = invokeWithRetry(prompt, sessionId, "solo-v12-retry");
                if (llmResponse == null) {
                    saveFailedReport(sessionId, true);
                    return;
                }
                parsed = parser.parse(llmResponse);
                if (!parsed.hasV12Content()) {
                    log.error("V12 content still incomplete after retry for solo report {}", sessionId);
                    saveFailedReport(sessionId, true);
                    return;
                }
            }

            Report report = buildSoloReport(session, parsed);
            reportRepo.save(report);

            sessionRepo.findById(sessionId).ifPresent(s -> {
                s.setReportId(report.getId());
                sessionRepo.save(s);
            });

            long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Solo report generated for session {} in {}ms", sessionId, duration);

        } catch (Exception e) {
            log.error("Failed to generate solo report for session {}", sessionId, e);
            saveFailedReport(sessionId, true);
        }
    }

    @Async
    public void generateDuoReport(String sessionId) {
        log.info("Generating duo report for session {}", sessionId);
        Instant startTime = Instant.now();

        try {
            Session session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

            List<Message> allMessages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
            String promptA = buildDuoReportPrompt(session, allMessages, MessageSender.USER_A);
            String promptB = buildDuoReportPrompt(session, allMessages, MessageSender.USER_B);

            CompletableFuture<String> futureA = CompletableFuture.supplyAsync(() ->
                invokeWithRetry(promptA, sessionId, "duo-A"), reportExecutor);
            CompletableFuture<String> futureB = CompletableFuture.supplyAsync(() ->
                invokeWithRetry(promptB, sessionId, "duo-B"), reportExecutor);

            String responseA = futureA.join();
            String responseB = futureB.join();

            if (responseA == null || responseB == null) {
                log.error("Duo report LLM failed for session {}", sessionId);
                saveFailedReport(sessionId, false);
                return;
            }

            ReportResponseParser.ParsedReport parsedA = parser.parse(responseA);
            ReportResponseParser.ParsedReport parsedB = parser.parse(responseB);

            RatioEnforcer.Enforced ratio = ratioEnforcer.enforce(
                parsedA.getContributionRatio() != null ? parsedA.getContributionRatio().a : 50,
                parsedB.getContributionRatio() != null ? parsedB.getContributionRatio().b : 50,
                session.getConflictType()
            );

            Report report = buildDuoReport(session, parsedA, parsedB, ratio);
            reportRepo.save(report);

            sessionRepo.findById(sessionId).ifPresent(s -> {
                s.setReportId(report.getId());
                sessionRepo.save(s);
            });

            long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
            log.info("Duo report generated for session {} in {}ms", sessionId, duration);

        } catch (Exception e) {
            log.error("Failed to generate duo report for session {}", sessionId, e);
            saveFailedReport(sessionId, false);
        }
    }

    private String buildSoloReportPrompt(Session session, List<Message> messages,
                                          MessageSender perspective) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(promptLoader.get("system.md")).append("\n\n");
        sb.append(promptLoader.get("gottman/four_horsemen.md")).append("\n\n");
        sb.append(promptLoader.get("nvc/four_steps.md")).append("\n\n");
        String userId = (perspective == MessageSender.USER_A)
            ? session.getUserAId() : session.getUserBId();
        String soloProfile = profileFragment.render(loadUserSafely(userId));
        if (!soloProfile.isEmpty()) {
            sb.append(soloProfile).append("\n");
        }
        sb.append(promptLoader.get("relations/" + session.getRelationType().getValue() + ".md")).append("\n\n");
        sb.append(promptLoader.get("chat/solo_report.md")).append("\n\n");
        String soloCtx = reportContextAssembler.assemble(session);
        if (!soloCtx.isEmpty()) sb.append(soloCtx).append("\n\n");
        appendConversationHistory(sb, messages, true);
        if (session.getConflictType() != null) {
            sb.append("<conflict_type>").append(session.getConflictType()).append("</conflict_type>\n\n");
        }
        // NOTE: _response_instructions.md intentionally omitted — it's for chat, not report JSON
        return sb.toString();
    }

    private String buildDuoReportPrompt(Session session, List<Message> messages,
                                         MessageSender perspective) throws Exception {
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
        String duoCtx = reportContextAssembler.assemble(session);
        if (!duoCtx.isEmpty()) sb.append(duoCtx).append("\n\n");
        appendConversationHistory(sb, messages, false);
        sb.append("<perspective>").append(perspective == MessageSender.USER_A ? "A" : "B").append("</perspective>\n\n");
        if (session.getConflictType() != null) {
            sb.append("<conflict_type>").append(session.getConflictType()).append("</conflict_type>\n\n");
        }
        // NOTE: _response_instructions.md intentionally omitted — it's for chat, not report JSON
        return sb.toString();
    }

    private void appendConversationHistory(StringBuilder sb, List<Message> messages,
                                           boolean skipSystemNotices) {
        sb.append("<conversation_history>\n");
        for (var msg : messages) {
            if (skipSystemNotices && (Boolean.TRUE.equals(msg.getIsPartnerJoinNotice())
                    || Boolean.TRUE.equals(msg.getIsFinalizeSuggestion()))) {
                continue;
            }
            sb.append("[").append(formatSender(msg.getSender())).append("] ")
              .append(msg.getContent()).append("\n");
        }
        sb.append("</conversation_history>\n\n");
    }

    private Report buildSoloReport(Session session, ReportResponseParser.ParsedReport parsed) {
        String reportId = newReportId();

        Report.Participant participantA = Report.Participant.builder()
            .userId(session.getCreatedByUserId()).build();
        Report.Participant participantB = session.getUserBId() != null
            ? Report.Participant.builder().userId(session.getUserBId()).build() : null;

        return Report.builder()
            .id(reportId)
            .sessionId(session.getId())
            .participantA(participantA)
            .participantB(participantB)
            .conflictType(session.getConflictType())
            .soloMode(true)
            .status(ReportStatus.OK)
            .llmProvider("claude-sonnet")
            .llmCallCount(1)
            // V12 fields
            .coreSummary(parsed.getCoreSummary())
            .fourStageFlow(mapStageFlows(parsed.getFourStageFlow()))
            .metaphorId(parsed.getMetaphor() != null ? parsed.getMetaphor().id : null)
            .metaphorDisplayName(parsed.getMetaphor() != null ? parsed.getMetaphor().displayName : null)
            .metaphorReason(parsed.getMetaphor() != null ? parsed.getMetaphor().reason : null)
            .nvcObservation(parsed.getNvcReflection() != null ? parsed.getNvcReflection().observation : null)
            .nvcFeeling(parsed.getNvcReflection() != null ? parsed.getNvcReflection().feeling : null)
            .nvcNeed(parsed.getNvcReflection() != null ? parsed.getNvcReflection().need : null)
            .nvcRequest(parsed.getNvcReflection() != null ? parsed.getNvcReflection().request : null)
            .recommendedActions(mapRecommendedActions(parsed.getRecommendedActions()))
            .externalResourceGuidance(mapExternalResource(parsed.getExternalResourceGuidance()))
            // Legacy text fields (kept for compat)
            .aPatternFeedback(parsed.getPatternFeedback())
            .suggestedApproach(parsed.getSuggestedApproach())
            .createdAt(Instant.now())
            .build();
    }

    private Report buildDuoReport(Session session, ReportResponseParser.ParsedReport parsedA,
                                   ReportResponseParser.ParsedReport parsedB,
                                   RatioEnforcer.Enforced ratio) {
        String reportId = newReportId();

        Report.ContributionRatio ratioObj = Report.ContributionRatio.builder()
            .a(ratio.a()).b(ratio.b())
            .clippedFrom(ratio.wasClipped() ? "llm" : null)
            .build();

        // Map nvcReflection → nvcScripts (aToB from A's nvcReflection, bToA from B's)
        Report.NVCScripts nvcScripts = buildNvcScriptsFromReflections(parsedA, parsedB);

        // Map fourHorsemenScores → FourHorsemenAnalysis
        Report.FourHorsemenAnalysis horsemen = null;
        if (parsedA.getFourHorsemenScores() != null) {
            ReportResponseParser.ParsedReport.FourHorsemenScores scores = parsedA.getFourHorsemenScores();
            horsemen = Report.FourHorsemenAnalysis.builder()
                .criticism(horsemenItemFromScore(scores.criticism))
                .contempt(horsemenItemFromScore(scores.contempt))
                .defensiveness(horsemenItemFromScore(scores.defensiveness))
                .stonewalling(horsemenItemFromScore(scores.stonewalling))
                .build();
        }

        return Report.builder()
            .id(reportId)
            .sessionId(session.getId())
            .participantA(Report.Participant.builder().userId(session.getCreatedByUserId()).build())
            .participantB(Report.Participant.builder().userId(session.getUserBId()).build())
            .conflictType(session.getConflictType())
            .soloMode(false)
            .status(ReportStatus.OK)
            .contributionRatio(ratioObj)
            .fourHorsemen(horsemen)
            .nvcScripts(nvcScripts)
            .llmProvider("claude-sonnet")
            .llmCallCount(2)
            // V12 fields from A's perspective as primary
            .coreSummary(parsedA.getCoreSummary())
            .fourStageFlow(mapStageFlows(parsedA.getFourStageFlow()))
            .metaphorId(parsedA.getMetaphor() != null ? parsedA.getMetaphor().id : null)
            .metaphorDisplayName(parsedA.getMetaphor() != null ? parsedA.getMetaphor().displayName : null)
            .metaphorReason(parsedA.getMetaphor() != null ? parsedA.getMetaphor().reason : null)
            .nvcObservation(parsedA.getNvcReflection() != null ? parsedA.getNvcReflection().observation : null)
            .nvcFeeling(parsedA.getNvcReflection() != null ? parsedA.getNvcReflection().feeling : null)
            .nvcNeed(parsedA.getNvcReflection() != null ? parsedA.getNvcReflection().need : null)
            .nvcRequest(parsedA.getNvcReflection() != null ? parsedA.getNvcReflection().request : null)
            .recommendedActions(mapRecommendedActions(parsedA.getRecommendedActions()))
            .externalResourceGuidance(mapExternalResource(
                parsedA.getExternalResourceGuidance() != null
                    ? parsedA.getExternalResourceGuidance()
                    : parsedB.getExternalResourceGuidance()))
            .aPatternFeedback(parsedA.getPatternFeedback())
            .suggestedApproach(parsedA.getSuggestedApproach())
            .createdAt(Instant.now())
            .build();
    }

    private Report.NVCScripts buildNvcScriptsFromReflections(
            ReportResponseParser.ParsedReport parsedA,
            ReportResponseParser.ParsedReport parsedB) {
        ReportResponseParser.ParsedReport.NVCReflection reflA = parsedA.getNvcReflection();
        ReportResponseParser.ParsedReport.NVCReflection reflB = parsedB.getNvcReflection();
        if (reflA == null && reflB == null) return null;
        return Report.NVCScripts.builder()
            .aToB(reflA != null ? Report.NVCScripts.NVCScript.builder()
                .observation(reflA.observation).feeling(reflA.feeling)
                .need(reflA.need).request(reflA.request).build() : null)
            .bToA(reflB != null ? Report.NVCScripts.NVCScript.builder()
                .observation(reflB.observation).feeling(reflB.feeling)
                .need(reflB.need).request(reflB.request).build() : null)
            .build();
    }

    private List<Report.StageFlow> mapStageFlows(
            List<ReportResponseParser.ParsedReport.StageFlow> src) {
        if (src == null || src.isEmpty()) return null;
        List<Report.StageFlow> result = new ArrayList<>();
        for (var s : src) {
            result.add(Report.StageFlow.builder()
                .stage(s.stage).stageName(s.stageName)
                .userQuote(s.userQuote).interpretation(s.interpretation)
                .build());
        }
        return result;
    }

    private List<Report.RecommendedAction> mapRecommendedActions(
            List<ReportResponseParser.ParsedReport.RecommendedAction> src) {
        if (src == null || src.isEmpty()) return null;
        List<Report.RecommendedAction> result = new ArrayList<>();
        for (var a : src) {
            result.add(Report.RecommendedAction.builder()
                .action(a.action).rationale(a.rationale).isUserChosen(a.isUserChosen)
                .build());
        }
        return result;
    }

    private Report.ExternalResourceGuidance mapExternalResource(
            ReportResponseParser.ParsedReport.ExternalResourceGuidance src) {
        if (src == null) return null;
        return Report.ExternalResourceGuidance.builder()
            .domain(src.domain).resource(src.resource).rationale(src.rationale)
            .build();
    }

    private Report.FourHorsemenAnalysis.HorsemenItem horsemenItemFromScore(int score) {
        boolean detected = score > 0;
        String intensity = score >= 7 ? "high" : score >= 4 ? "medium" : score >= 1 ? "low" : "";
        return Report.FourHorsemenAnalysis.HorsemenItem.builder()
            .detected(detected).intensity(intensity).build();
    }

    private String invokeWithRetry(String prompt, String sessionId, String label) {
        try {
            return llmBridge.invoke(prompt, reportModel);
        } catch (Exception firstEx) {
            log.warn("LLM first attempt failed for {} report {}: {}", label, sessionId, firstEx.getMessage());
            try {
                return llmBridge.invoke(prompt, reportModel);
            } catch (Exception retryEx) {
                log.error("LLM retry also failed for {} report {}: {}", label, sessionId, retryEx.getMessage());
                return null;
            }
        }
    }

    private void saveFailedReport(String sessionId, boolean soloMode) {
        try {
            reportRepo.save(Report.builder()
                .id(newReportId())
                .sessionId(sessionId)
                .soloMode(soloMode)
                .status(ReportStatus.FAILED)
                .llmProvider("error-failed")
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.error("Even failed report save failed for session {}", sessionId, e);
        }
    }

    private User loadUserSafely(String userId) {
        if (userId == null) return null;
        try {
            return userRepo.findByIdAndDeletedAtIsNull(userId).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to load user {} for report: {}", userId, e.getMessage());
            return null;
        }
    }

    private String newReportId() {
        return "rep_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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
