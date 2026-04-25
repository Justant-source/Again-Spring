package com.againspring.service;

import com.againspring.domain.enums.SessionStatus;
import com.againspring.domain.Report;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.llm.LLMException;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.LLMRequest;
import com.againspring.llm.LLMResponse;
import com.againspring.llm.prompt.PromptAssembler;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.RatioEnforcer;
import com.againspring.service.report.NVCValidator;
import com.againspring.service.report.NeedsMapValidator;
import com.againspring.service.report.ReportResponseParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generates comprehensive mediation reports after session completion.
 * Idempotent: if report already exists, returns existing report.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final SessionRepository sessionRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final LLMProvider llmProvider;
    private final PromptAssembler promptAssembler;
    private final RatioEnforcer ratioEnforcer;
    private final KeywordGuard keywordGuard;
    private final ReportResponseParser reportResponseParser;
    private final NVCValidator nvcValidator;
    private final NeedsMapValidator needsMapValidator;
    private final Clock clock;

    /**
     * Generates a report for a completed session.
     * Idempotent: if report exists, returns it without regenerating.
     *
     * @param sessionId Session ID
     * @return Generated or existing Report
     */
    @Async
    public void generateAsync(String sessionId) {
        try {
            generate(sessionId);
        } catch (Exception e) {
            log.error("Failed to generate report for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Synchronous version of report generation.
     */
    public Report generate(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new RuntimeException("Session is not completed: " + session.getStatus());
        }

        // Idempotence check
        if (session.getReportId() != null) {
            return reportRepository.findById(session.getReportId())
                    .orElseThrow(() -> new RuntimeException("Report not found for session: " + sessionId));
        }

        long startTime = System.currentTimeMillis();

        // 1. Assemble report generation prompt with full transcript
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("reportGeneration", true);
        if (Boolean.TRUE.equals(session.getSoloMode())) {
            metadata.put("soloMode", true);
        }

        // Build a simple text representation of turns for LLM context
        StringBuilder turnContext = new StringBuilder();
        for (int i = 0; i < session.getTurns().size(); i++) {
            var turn = session.getTurns().get(i);
            turnContext.append(String.format("Turn %d (%s): %s\n", turn.getTurnNumber(), turn.getRole(), turn.getContent()));
        }

        String userInput = "Please analyze the entire mediation session and provide a comprehensive report with: " +
                "1. Contribution ratio (A% vs B%), 2. Four horsemen detection, 3. NVC scripts, 4. Needs map positions, " +
                "5. Repair suggestions.\n\n" + turnContext.toString();

        LLMRequest request = promptAssembler.assemble(
                0, // Special turn number for report
                null, // No specific role
                session.getRelationType().name().toLowerCase(),
                session.getConflictType(),
                userInput,
                UUID.randomUUID().toString(),
                metadata
        );

        // 2. Call LLM with timeout
        LLMResponse llmResponse;
        try {
            llmResponse = llmProvider.invoke(request);
        } catch (LLMException e) {
            log.warn("LLM invocation failed for report generation: {}", e.getMessage());
            // Create a minimal valid report with fallback values
            return createFallbackReport(session);
        }

        // 3. Parse JSON response
        ReportResponseParser.ParsedReport parsed = reportResponseParser.parse(llmResponse.getRawText());

        // 4. Validate and clip results
        Report report = Report.builder()
                .id(null) // MongoDB generates
                .sessionId(sessionId)
                .conflictType(parsed.conflictType != null ? parsed.conflictType : session.getConflictType())
                .soloMode(Boolean.TRUE.equals(session.getSoloMode()))
                .build();

        // Validate and clip contribution ratio
        if (parsed.contributionRatio != null) {
            com.againspring.safety.EnforcedRatio enforced = ratioEnforcer.clip(
                    report.getConflictType(),
                    parsed.contributionRatio.a,
                    parsed.contributionRatio.b,
                    parsed.contributionRatio.rationale
            );

            report.setContributionRatio(Report.ContributionRatio.builder()
                    .a(enforced.getAPct())
                    .b(enforced.getBPct())
                    .label(parsed.contributionRatio.label != null ?
                            Report.ContributionRatio.RatioLabel.builder()
                                    .a(parsed.contributionRatio.label.a)
                                    .b(parsed.contributionRatio.label.b)
                                    .build() : null)
                    .clippedFrom(enforced.isWasClipped() ? enforced.getOriginalA() + ":" + enforced.getOriginalB() : null)
                    .rationale(parsed.contributionRatio.rationale)
                    .build());

            log.info("Ratio clipped: was={}:{} now={}:{}", enforced.getOriginalA(), enforced.getOriginalB(),
                    enforced.getAPct(), enforced.getBPct());
        }

        // Validate and apply NVC scripts
        if (parsed.nvcScripts != null) {
            Report.NVCScripts nvc = new Report.NVCScripts();

            if (parsed.nvcScripts.aToB != null && nvcValidator.validate(convertParsedToReportNVC(parsed.nvcScripts.aToB))) {
                nvc.aToB = convertParsedToReportNVC(parsed.nvcScripts.aToB);
            } else {
                nvc.aToB = nvcValidator.createFallback();
            }

            if (parsed.nvcScripts.bToA != null && nvcValidator.validate(convertParsedToReportNVC(parsed.nvcScripts.bToA))) {
                nvc.bToA = convertParsedToReportNVC(parsed.nvcScripts.bToA);
            } else if (!Boolean.TRUE.equals(session.getSoloMode())) {
                nvc.bToA = nvcValidator.createFallback();
            }

            report.setNvcScripts(nvc);
        }

        if (parsed.needsMap != null && needsMapValidator.validateMap(convertParsedToReportNeedsMap(parsed.needsMap))) {
            report.setNeedsMap(convertParsedToReportNeedsMap(parsed.needsMap));
        }

        // Validate four horsemen
        if (parsed.fourHorsemen != null) {
            report.setFourHorsemen(convertParsedToReportHorsemen(parsed.fourHorsemen));
        }

        // Apply keyword filter to free-text fields
        if (parsed.repairSuggestions != null && !parsed.repairSuggestions.isEmpty()) {
            report.setRepairSuggestions(parsed.repairSuggestions.stream()
                    .map(keywordGuard::applyOutputFilter)
                    .toList());
        }

        // Add participant snapshots
        User userA = userRepository.findById(session.getCreatedByUserId()).orElse(null);
        User userB = session.getInviteeUserId() != null ? userRepository.findById(session.getInviteeUserId()).orElse(null) : null;

        report.setParticipantA(Report.Participant.builder()
                .userId(session.getCreatedByUserId())
                .nicknameSnapshot(userA != null ? userA.getNickname() : "A")
                .build());

        if (userB != null) {
            report.setParticipantB(Report.Participant.builder()
                    .userId(session.getInviteeUserId())
                    .nicknameSnapshot(userB.getNickname())
                    .build());
        }

        report.setLlmProvider(llmResponse.getProvider());
        report.setGenerationDurationMs(System.currentTimeMillis() - startTime);
        report.setCreatedAt(Instant.now(clock));

        // 5. Persist report
        Report saved = reportRepository.save(report);
        session.setReportId(saved.getId());
        sessionRepository.save(session);

        log.info("Report generated: id={}, session={}, duration={}ms", saved.getId(), sessionId,
                saved.getGenerationDurationMs());

        return saved;
    }

    /**
     * Creates a minimal fallback report when LLM generation fails.
     */
    private Report createFallbackReport(Session session) {
        return Report.builder()
                .sessionId(session.getId())
                .conflictType(session.getConflictType())
                .soloMode(Boolean.TRUE.equals(session.getSoloMode()))
                .createdAt(Instant.now(clock))
                .build();
    }

    private Report.NVCScripts.NVCScript convertParsedToReportNVC(ReportResponseParser.ParsedReport.NVCScript parsed) {
        return Report.NVCScripts.NVCScript.builder()
                .observation(parsed.observation)
                .feeling(parsed.feeling)
                .need(parsed.need)
                .request(parsed.request)
                .build();
    }

    private Report.NeedsMap convertParsedToReportNeedsMap(ReportResponseParser.ParsedReport.NeedsMap parsed) {
        return Report.NeedsMap.builder()
                .axisX(parsed.axisX)
                .axisXLabel(parsed.axisXLabel)
                .axisY(parsed.axisY)
                .axisYLabel(parsed.axisYLabel)
                .positionA(parsed.positionA != null ? Report.NeedsMap.Position.builder()
                        .x(needsMapValidator.clipCoordinate(parsed.positionA.x))
                        .y(needsMapValidator.clipCoordinate(parsed.positionA.y))
                        .build() : null)
                .positionB(parsed.positionB != null ? Report.NeedsMap.Position.builder()
                        .x(needsMapValidator.clipCoordinate(parsed.positionB.x))
                        .y(needsMapValidator.clipCoordinate(parsed.positionB.y))
                        .build() : null)
                .interpretation(parsed.interpretation)
                .build();
    }

    private Report.FourHorsemenAnalysis convertParsedToReportHorsemen(ReportResponseParser.ParsedReport.FourHorsemen parsed) {
        return Report.FourHorsemenAnalysis.builder()
                .criticism(convertHorsemenItem(parsed.criticism))
                .defensiveness(convertHorsemenItem(parsed.defensiveness))
                .contempt(convertHorsemenItem(parsed.contempt))
                .stonewalling(convertHorsemenItem(parsed.stonewalling))
                .build();
    }

    private Report.FourHorsemenAnalysis.HorsemenItem convertHorsemenItem(ReportResponseParser.ParsedReport.HorsemenItem parsed) {
        return Report.FourHorsemenAnalysis.HorsemenItem.builder()
                .detected(parsed.detected)
                .intensity(parsed.intensity)
                .examples(parsed.examples)
                .build();
    }
}
