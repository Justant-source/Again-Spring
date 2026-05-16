package com.againspring.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "서비스 헬스 체크")
public class HealthController {

    @GetMapping
    @Operation(summary = "서비스 상태 확인", description = "서비스 liveness probe — 인증 불필요")
    @ApiResponse(responseCode = "200", description = "서비스 정상 (status=UP)")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "againspring");
        response.put("timestamp", Instant.now());
        return ResponseEntity.ok(response);
    }
}
