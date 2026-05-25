package com.againspring.service.report;

import com.againspring.api.dto.response.ReportResponse;
import com.againspring.domain.Report;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Report 엔티티 → ReportResponse DTO 매핑 컴포넌트.
 * ReportController 및 admin 마케팅 엔드포인트에서 공유 사용.
 */
@Component
public class ReportResponseMapper {

    public ReportResponse toResponse(Report report) {
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
        if (participant == null) return null;
        return ReportResponse.ParticipantSnapshot.builder()
                .userId(participant.userId)
                .nicknameSnapshot(participant.nicknameSnapshot)
                .guestName(participant.guestName)
                .build();
    }

    private ReportResponse.ContributionRatioResponse mapContributionRatio(Report.ContributionRatio ratio) {
        if (ratio == null) return null;
        return ReportResponse.ContributionRatioResponse.builder()
                .a(ratio.a).b(ratio.b)
                .label(ratio.label != null ? ReportResponse.ContributionRatioResponse.RatioLabel.builder()
                        .a(ratio.label.a).b(ratio.label.b).build() : null)
                .clippedFrom(ratio.clippedFrom)
                .rationale(ratio.rationale)
                .build();
    }

    private ReportResponse.NeedsMapResponse mapNeedsMap(Report.NeedsMap needsMap) {
        if (needsMap == null) return null;
        return ReportResponse.NeedsMapResponse.builder()
                .axisX(needsMap.axisX).axisXLabel(needsMap.axisXLabel)
                .axisY(needsMap.axisY).axisYLabel(needsMap.axisYLabel)
                .positionA(needsMap.positionA != null ? ReportResponse.NeedsMapResponse.Position.builder()
                        .x(needsMap.positionA.x).y(needsMap.positionA.y).build() : null)
                .positionB(needsMap.positionB != null ? ReportResponse.NeedsMapResponse.Position.builder()
                        .x(needsMap.positionB.x).y(needsMap.positionB.y).build() : null)
                .interpretation(needsMap.interpretation)
                .build();
    }

    private ReportResponse.NVCScriptsResponse mapNvcScripts(Report.NVCScripts scripts) {
        if (scripts == null) return null;
        return ReportResponse.NVCScriptsResponse.builder()
                .aToB(mapNvcScript(scripts.aToB))
                .bToA(mapNvcScript(scripts.bToA))
                .build();
    }

    private ReportResponse.NVCScriptsResponse.NVCScript mapNvcScript(Report.NVCScripts.NVCScript script) {
        if (script == null) return null;
        return ReportResponse.NVCScriptsResponse.NVCScript.builder()
                .observation(script.observation).feeling(script.feeling)
                .need(script.need).request(script.request)
                .build();
    }
}
