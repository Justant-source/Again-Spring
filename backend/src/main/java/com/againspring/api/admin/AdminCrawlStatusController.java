package com.againspring.api.admin;

import com.againspring.api.dto.response.CrawlStatusResponse;
import com.againspring.service.admin.AdminCrawlStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 크롤 상태 모니터링 — 최근 24시간 저장 건수·신선도 배지용.
 *
 * 36일 침묵 사고(2026-06-24~07-30) 재발 방지 목적.
 * 서버에서 24시간 통계를 미리 계산하므로 프론트엔드는 응답을 그대로 표시.
 */
@RestController
@RequestMapping("/api/admin/crawl-status")
@RequiredArgsConstructor
@Tag(name = "Admin — Crawl Status", description = "AI Learning 크롤 신선도 조회 (ADMIN 전용)")
public class AdminCrawlStatusController {

    private final AdminCrawlStatusService crawlStatusService;

    /**
     * 크롤 신선도 조회.
     *
     * 응답:
     * - savedBySource24h: 소스별 최근 24시간 저장 건수 합계
     * - lastSuccessfulAt: 소스별 마지막 성공 시각
     * - failureCount24h: 24시간 내 실패 건수
     * - stale: true이면 24시간 내 성공 크롤 0건 (배지 "stale" 표시)
     * - checkedAt: 조회 시각 (기준점)
     * - errorMessage: 조회 오류 시 메시지 (정상이면 null)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
        summary = "크롤 신선도 조회",
        description = "AI Learning 서비스의 최근 크롤 로그를 조회해 24시간 통계를 반환. ADMIN 권한 필요."
    )
    @ApiResponse(
        responseCode = "200",
        description = "크롤 상태 반환. stale=true이면 배지 '신선하지 않음' 표시."
    )
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<CrawlStatusResponse> getCrawlStatus() {
        CrawlStatusResponse response = crawlStatusService.getCrawlStatus();
        return ResponseEntity.ok(response);
    }
}
