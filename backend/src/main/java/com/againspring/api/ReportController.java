package com.againspring.api;

import com.againspring.api.dto.response.ReportResponse;
import com.againspring.domain.Report;
import com.againspring.repository.ReportRepository;
import com.againspring.repository.SessionRepository;
// import com.againspring.service.ReportService; — REMOVED (V1.5)
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for report endpoints.
 * - POST /api/sessions/{sessionId}/report — trigger report generation
 * - GET  /api/sessions/{sessionId}/report — retrieve report by sessionId (FE 사용)
 * - GET  /api/reports/{reportId}          — retrieve generated report by reportId
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Report", description = "갈등 분석 리포트 생성·조회")
public class ReportController {

    private final ReportRepository reportRepository;
    private final SessionRepository sessionRepository;
    private final com.againspring.service.report.ReportResponseMapper reportMapper;

    /**
     * Trigger report generation for a completed session.
     * POST /api/sessions/{sessionId}/report
     *
     * Response 202 (Accepted): Report generation started asynchronously.
     * Response 400: Session not completed or already has report.
     */
    @PostMapping("/sessions/{sessionId}/report")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "리포트 생성 요청", description = "완료된 세션의 리포트를 비동기로 생성 요청한다. 세션 참여자(owner)만 호출 가능. 202 반환 후 GET으로 폴링.")
    @ApiResponse(responseCode = "202", description = "리포트 생성 시작 (status=generating, estimatedSeconds 포함)")
    @ApiResponse(responseCode = "400", description = "세션 미완료 또는 이미 리포트 존재")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "세션 참여자 아님")
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

            // TODO V1.5: Trigger async report generation
            // reportService.generateAsync(sessionId);

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
     * Retrieve a report by sessionId (FE primary use).
     * GET /api/sessions/{sessionId}/report
     * Response 200: Full report details.
     * Response 404: Report not yet generated (FE should poll).
     * Response 403: Not a participant.
     */
    @GetMapping("/sessions/{sessionId}/report")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "세션 ID로 리포트 조회", description = "세션에 대한 리포트를 조회한다. 미생성 시 404 — FE는 폴링. 세션 참여자만 접근 가능.")
    @ApiResponse(responseCode = "200", description = "리포트 상세")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "세션 참여자 아님")
    @ApiResponse(responseCode = "404", description = "리포트 미생성 (폴링 대기)")
    public ResponseEntity<ReportResponse> getReportBySession(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails != null ? userDetails.getUsername() : null;

        try {
            var session = sessionRepository.findById(sessionId)
                    .orElse(null);
            if (session != null && userId != null) {
                boolean isAdmin = userDetails.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                if (isAdmin && Boolean.TRUE.equals(session.getTestRun())) {
                    return reportRepository.findBySessionId(sessionId)
                            .map(report -> ResponseEntity.ok(reportMapper.toResponse(report)))
                            .orElseGet(() -> ResponseEntity.notFound().build());
                }
                boolean isParticipant = userId.equals(session.getCreatedByUserId())
                        || userId.equals(session.getInviteeUserId());
                if (!isParticipant) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }

            return reportRepository.findBySessionId(sessionId)
                    .map(report -> ResponseEntity.ok(reportMapper.toResponse(report)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to get report for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.notFound().build();
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
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "리포트 ID로 직접 조회", description = "reportId로 리포트를 직접 조회한다. 세션 참여자만 접근 가능.")
    @ApiResponse(responseCode = "200", description = "리포트 상세")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "세션 참여자 아님")
    @ApiResponse(responseCode = "404", description = "리포트 없음")
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

            ReportResponse response = reportMapper.toResponse(report);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to retrieve report {}: {}", reportId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

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
                // V12 fields
                .status(report.getStatus() != null ? report.getStatus().name() : "OK")
                .coreSummary(report.getCoreSummary())
                .fourStageFlow(mapStageFlows(report.getFourStageFlow()))
                .metaphorId(report.getMetaphorId())
                .metaphorDisplayName(report.getMetaphorDisplayName())
                .metaphorReason(report.getMetaphorReason())
                .nvcObservation(report.getNvcObservation())
                .nvcFeeling(report.getNvcFeeling())
                .nvcNeed(report.getNvcNeed())
                .nvcRequest(report.getNvcRequest())
                .recommendedActions(mapRecommendedActions(report.getRecommendedActions()))
                .externalResourceGuidance(mapExternalResource(report.getExternalResourceGuidance()))
                .build();
    }

    private List<ReportResponse.StageFlowResponse> mapStageFlows(List<Report.StageFlow> flows) {
        if (flows == null) return null;
        return flows.stream().map(f -> ReportResponse.StageFlowResponse.builder()
            .stage(f.getStage()).stageName(f.getStageName())
            .userQuote(f.getUserQuote()).interpretation(f.getInterpretation())
            .build()).toList();
    }

    private List<ReportResponse.RecommendedActionResponse> mapRecommendedActions(
            List<Report.RecommendedAction> actions) {
        if (actions == null) return null;
        return actions.stream().map(a -> ReportResponse.RecommendedActionResponse.builder()
            .action(a.getAction()).rationale(a.getRationale())
            .isUserChosen(Boolean.TRUE.equals(a.getIsUserChosen()))
            .build()).toList();
    }

    private ReportResponse.ExternalResourceResponse mapExternalResource(
            Report.ExternalResourceGuidance src) {
        if (src == null) return null;
        return ReportResponse.ExternalResourceResponse.builder()
            .domain(src.getDomain()).resource(src.getResource()).rationale(src.getRationale())
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
     */
    private String extractUserIdFromPrincipal(org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }
        return principal.getUsername();
    }
}
