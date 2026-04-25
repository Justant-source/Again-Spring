package com.againspring.api;

import com.againspring.api.dto.response.ReportResponse;
import com.againspring.domain.Report;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
import com.againspring.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for report endpoints.
 * - POST /api/sessions/{sessionId}/report — trigger report generation
 * - GET /api/reports/{reportId} — retrieve generated report
 *
 * TODO Phase 3-5 integration: User principal extraction.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportRepository reportRepository;
    private final SessionRepository sessionRepository;

    /**
     * Trigger report generation for a completed session.
     * POST /api/sessions/{sessionId}/report
     *
     * Response 202 (Accepted): Report generation started asynchronously.
     * Response 400: Session not completed or already has report.
     */
    @PostMapping("/sessions/{sessionId}/report")
    public ResponseEntity<Map<String, Object>> triggerReportGeneration(
            @PathVariable String sessionId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        String currentUserId = extractUserIdFromPrincipal(principal);

        try {
            var session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            // Verify user is owner
            if (!session.getCreatedByUserId().equals(currentUserId) &&
                    !session.getInviteeUserId().equals(currentUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // Trigger async report generation
            reportService.generateAsync(sessionId);

            Map<String, Object> response = new HashMap<>();
            response.put("reportId", "generating");
            response.put("status", "generating");
            response.put("estimatedSeconds", 15);

            return ResponseEntity.accepted().body(response);
        } catch (RuntimeException e) {
            log.error("Failed to trigger report generation for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retrieve a generated report.
     * GET /api/reports/{reportId}
     *
     * Response 200: Full report details.
     * Response 403: User is not a participant.
     * Response 404: Report not found.
     */
    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ReportResponse> getReport(
            @PathVariable String reportId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        String currentUserId = extractUserIdFromPrincipal(principal);

        try {
            Report report = reportRepository.findById(reportId)
                    .orElseThrow(() -> new RuntimeException("Report not found"));

            // Verify access: user must be one of the participants
            var session = sessionRepository.findById(report.getSessionId())
                    .orElseThrow(() -> new RuntimeException("Session not found"));

            if (!session.getCreatedByUserId().equals(currentUserId) &&
                    !session.getInviteeUserId().equals(currentUserId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            ReportResponse response = mapToResponse(report);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to retrieve report {}: {}", reportId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Map Report entity to ReportResponse DTO.
     */
    private ReportResponse mapToResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .sessionId(report.getSessionId())
                .conflictType(report.getConflictType())
                .isSoloMode(Boolean.TRUE.equals(report.getSoloMode()))
                .participantA(mapParticipant(report.getParticipantA()))
                .participantB(mapParticipant(report.getParticipantB()))
                .contributionRatio(mapContributionRatio(report.getContributionRatio()))
                .needsMap(mapNeedsMap(report.getNeedsMap()))
                .nvcScripts(mapNvcScripts(report.getNvcScripts()))
                .repairSuggestions(report.getRepairSuggestions())
                .aPatternFeedback(report.getAPatternFeedback())
                .suggestedApproach(report.getSuggestedApproach())
                .inviteAgainCTA(report.getInviteAgainCTA())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private ReportResponse.ParticipantSnapshot mapParticipant(Report.Participant participant) {
        if (participant == null) {
            return null;
        }
        return ReportResponse.ParticipantSnapshot.builder()
                .userId(participant.userId)
                .nicknameSnapshot(participant.nicknameSnapshot)
                .guestName(participant.guestName)
                .build();
    }

    private ReportResponse.ContributionRatioResponse mapContributionRatio(Report.ContributionRatio ratio) {
        if (ratio == null) {
            return null;
        }
        return ReportResponse.ContributionRatioResponse.builder()
                .a(ratio.a)
                .b(ratio.b)
                .label(ratio.label != null ? ReportResponse.ContributionRatioResponse.RatioLabel.builder()
                        .a(ratio.label.a)
                        .b(ratio.label.b)
                        .build() : null)
                .clippedFrom(ratio.clippedFrom)
                .rationale(ratio.rationale)
                .build();
    }

    private ReportResponse.NeedsMapResponse mapNeedsMap(Report.NeedsMap needsMap) {
        if (needsMap == null) {
            return null;
        }
        return ReportResponse.NeedsMapResponse.builder()
                .axisX(needsMap.axisX)
                .axisXLabel(needsMap.axisXLabel)
                .axisY(needsMap.axisY)
                .axisYLabel(needsMap.axisYLabel)
                .positionA(needsMap.positionA != null ? ReportResponse.NeedsMapResponse.Position.builder()
                        .x(needsMap.positionA.x)
                        .y(needsMap.positionA.y)
                        .build() : null)
                .positionB(needsMap.positionB != null ? ReportResponse.NeedsMapResponse.Position.builder()
                        .x(needsMap.positionB.x)
                        .y(needsMap.positionB.y)
                        .build() : null)
                .interpretation(needsMap.interpretation)
                .build();
    }

    private ReportResponse.NVCScriptsResponse mapNvcScripts(Report.NVCScripts scripts) {
        if (scripts == null) {
            return null;
        }
        return ReportResponse.NVCScriptsResponse.builder()
                .aToB(mapNvcScript(scripts.aToB))
                .bToA(mapNvcScript(scripts.bToA))
                .build();
    }

    private ReportResponse.NVCScriptsResponse.NVCScript mapNvcScript(Report.NVCScripts.NVCScript script) {
        if (script == null) {
            return null;
        }
        return ReportResponse.NVCScriptsResponse.NVCScript.builder()
                .observation(script.observation)
                .feeling(script.feeling)
                .need(script.need)
                .request(script.request)
                .build();
    }

    /**
     * Extract user ID from JWT principal.
     * TODO Phase 3-5 integration: Adjust based on your actual JWT principal structure.
     */
    private String extractUserIdFromPrincipal(org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }
        return principal.getUsername();
    }
}
