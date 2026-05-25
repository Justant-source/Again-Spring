package com.againspring.api.admin.marketing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * V15 마케팅 자동화 모듈 스텁 컨트롤러.
 * 실제 엔드포인트는 V15.2~V15.6에서 추가됩니다.
 * (소스 스토리 수집·시뮬레이션·콘텐츠 생성·배포 자동화)
 */
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/admin/marketing")
@Tag(name = "Admin — Marketing Automation (V15)", description = "마케팅 자동화 모듈 (ADMIN 전용, dev 환경 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class MarketingModuleController {

    @GetMapping("/health")
    @Operation(summary = "마케팅 모듈 헬스 체크", description = "마케팅 자동화 모듈 상태 확인")
    @ApiResponse(responseCode = "200", description = "모듈 정상 작동")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "module", "marketing-v15",
                "message", "Marketing automation module stub ready for V15.2+"
        ));
    }
}
