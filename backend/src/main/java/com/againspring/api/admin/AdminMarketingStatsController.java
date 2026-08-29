package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.domain.marketing.MarketingStatsEvent;
import com.againspring.marketing.AcquisitionFunnelService;
import com.againspring.marketing.MarketingStatsDashboardService;
import com.againspring.marketing.MarketingStatsEventService;
import com.againspring.marketing.MarketingThemeBoostService;
import com.againspring.marketing.MarketingThemeProposeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin Phase 3 marketing stats tab API (dashboard · theme matrix · events).
 * Collect endpoints remain on {@link AdminMarketingController} under {@code /stats/collect*}.
 */
@RestController
@RequestMapping("/api/admin/marketing/stats")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing Stats", description = "마케팅 통계 탭 · 테마 배수 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketingStatsController {

    private final MarketingStatsDashboardService dashboardService;
    private final AcquisitionFunnelService acquisitionFunnelService;
    private final MarketingThemeProposeService themeProposeService;
    private final MarketingThemeBoostService themeBoostService;
    private final MarketingStatsEventService statsEventService;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard")
    @Operation(summary = "Marketing stats dashboard",
        description = "플랫폼 KPI·UTM·수집 건강 (Phase 3). weeksAgo=0 이번 주(KST).")
    @ApiResponse(responseCode = "200", description = "Dashboard returned")
    public ResponseEntity<MarketingStatsDashboardService.DashboardDto> dashboard(
            @RequestParam(required = false) String platform,
            @RequestParam(defaultValue = "0") int weeksAgo,
            @RequestParam(defaultValue = "7") int rangeDays,
            @RequestParam(required = false) String primaryMetric) {
        return ResponseEntity.ok(dashboardService.dashboard(
            platform, weeksAgo, rangeDays, primaryMetric));
    }

    @GetMapping("/acquisition")
    @Operation(summary = "유입 퍼널 (방문 → 고유 방문자 → 가입)",
        description = "봇 제외. 채널별·일별. 발행 건수 다음 칸을 채우는 지표 (2026-08-29).")
    @ApiResponse(responseCode = "200", description = "Funnel returned")
    public ResponseEntity<AcquisitionFunnelService.FunnelDto> acquisition(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(acquisitionFunnelService.funnel(days));
    }

    @GetMapping("/theme-matrix")
    @Operation(summary = "Theme emotion×category matrix",
        description = "히트맵 셀 + 제안·말린 축 제안 (저장 없음)")
    @ApiResponse(responseCode = "200", description = "Matrix returned")
    @ApiResponse(responseCode = "400", description = "Unknown platform")
    public ResponseEntity<MarketingThemeProposeService.ThemeMatrixView> themeMatrix(
            @RequestParam String platform,
            @RequestParam(defaultValue = "0") int weeksAgo) {
        return ResponseEntity.ok(themeProposeService.buildMatrix(platform, weeksAgo));
    }

    @PostMapping("/theme-matrix/propose")
    @Operation(summary = "Recompute theme boost proposals",
        description = "제안 재계산만 (배수는 저장하지 않음). PROPOSE 이벤트 기록.")
    @ApiResponse(responseCode = "200", description = "Proposals returned")
    @ApiResponse(responseCode = "400", description = "Unknown platform")
    @Auditable(action = "PROPOSE_MARKETING_THEME")
    public ResponseEntity<List<MarketingThemeProposeService.Proposal>> propose(
            @RequestParam String platform,
            @RequestParam(defaultValue = "0") int weeksAgo) {
        List<MarketingThemeProposeService.Proposal> proposals =
            themeProposeService.propose(platform, weeksAgo);
        recordEvent("PROPOSE", platform, Map.of(
            "weeksAgo", weeksAgo,
            "count", proposals.size()
        ));
        return ResponseEntity.ok(proposals);
    }

    @PostMapping("/theme-matrix/apply")
    @Operation(summary = "Apply theme boost changes",
        description = "confirm=true 필수. 쿨다운·Δ캡·범위 위반 시 400. APPLY 이벤트 기록.")
    @ApiResponse(responseCode = "200", description = "Applied")
    @ApiResponse(responseCode = "400", description = "Cooldown / range / confirm")
    @Auditable(action = "APPLY_MARKETING_THEME")
    public ResponseEntity<MarketingThemeBoostService.ApplyResult> apply(
            @RequestBody ThemeApplyRequest body) {
        if (body == null || body.platform() == null || body.platform().isBlank()) {
            throw new IllegalArgumentException("platform is required");
        }
        boolean confirm = body.confirm() != null && body.confirm();
        List<MarketingThemeBoostService.ThemeBoostChange> changes = new ArrayList<>();
        if (body.changes() != null) {
            for (ThemeChangeBody c : body.changes()) {
                if (c == null) {
                    continue;
                }
                double boost = c.boost() != null ? c.boost() : 1.0;
                changes.add(new MarketingThemeBoostService.ThemeBoostChange(
                    c.emotion(), c.category(), boost));
            }
        }
        MarketingThemeBoostService.ApplyResult result =
            themeBoostService.applyChanges(body.platform(), changes, confirm);
        recordEvent("APPLY", body.platform(), Map.of(
            "applied", result.applied(),
            "cooldownUntil", result.cooldownUntil() != null ? result.cooldownUntil().toString() : ""
        ));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/theme-boosts")
    @Operation(summary = "Stored theme boost matrix",
        description = "system_setting에 저장된 감정×카테고리 배수 맵")
    @ApiResponse(responseCode = "200", description = "Matrix returned")
    @ApiResponse(responseCode = "400", description = "Unknown platform")
    public ResponseEntity<Map<String, Object>> themeBoosts(@RequestParam String platform) {
        Map<String, Map<String, Double>> matrix = themeBoostService.getMatrix(platform);
        Instant cooldown = themeBoostService.cooldownUntil();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("platform", platform);
        body.put("matrix", matrix);
        body.put("shadow", themeBoostService.isShadow());
        body.put("cooldownUntil", cooldown != null ? cooldown.toString() : null);
        body.put("canApplyNow", themeBoostService.canApplyNow());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/events")
    @Operation(summary = "Marketing stats event timeline",
        description = "COLLECT_*/PROPOSE/APPLY/SHADOW_TOGGLE 최근 이력")
    @ApiResponse(responseCode = "200", description = "Events listed")
    public ResponseEntity<List<StatsEventDto>> events(
            @RequestParam(defaultValue = "50") int limit) {
        List<MarketingStatsEvent> rows = statsEventService.listRecent(limit);
        List<StatsEventDto> out = rows.stream()
            .map(e -> new StatsEventDto(
                e.getId(),
                e.getEventType(),
                e.getPlatform(),
                e.getPayloadJson(),
                e.getCreatedAt() != null ? e.getCreatedAt().toString() : null
            ))
            .toList();
        return ResponseEntity.ok(out);
    }

    private void recordEvent(String type, String platform, Map<String, Object> payload) {
        String json = null;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ignored) {
            json = "{}";
        }
        statsEventService.record(type, platform, json);
    }

    /** Body for POST /theme-matrix/apply. */
    public record ThemeApplyRequest(
        String platform,
        List<ThemeChangeBody> changes,
        Boolean confirm
    ) {}

    public record ThemeChangeBody(
        String emotion,
        String category,
        Double boost
    ) {}

    public record StatsEventDto(
        Long id,
        String eventType,
        String platform,
        String payloadJson,
        String createdAt
    ) {}
}
