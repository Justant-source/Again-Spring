package com.againspring.api.admin;

import com.againspring.api.dto.response.SystemHealthResponse;
import com.againspring.service.admin.SystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/health")
@RequiredArgsConstructor
@Tag(name = "Admin — Health", description = "시스템 상태 모니터링 (ADMIN 전용)")
public class AdminHealthController {

    private final SystemHealthService systemHealthService;

    @GetMapping("/system")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "시스템 헬스 상세 조회", description = "DB·LLM·디스크 등 서브시스템 상태를 반환한다. ADMIN 권한 필요.")
    @ApiResponse(responseCode = "200", description = "헬스 상태 반환")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<SystemHealthResponse> getSystemHealth() {
        return ResponseEntity.ok(systemHealthService.check());
    }
}
