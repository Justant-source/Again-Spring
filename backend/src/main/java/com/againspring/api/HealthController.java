package com.againspring.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "서비스 헬스 체크")
public class HealthController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    @Operation(summary = "서비스 상태 확인", description = "서비스 liveness probe — 인증 불필요. DB는 확인하지 않는다.")
    @ApiResponse(responseCode = "200", description = "서비스 정상 (status=UP)")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "againspring");
        response.put("timestamp", Instant.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/deep")
    @Operation(summary = "심층 상태 확인", description = "DB 연결까지 확인하는 readiness probe — 인증 불필요")
    @ApiResponse(responseCode = "200", description = "DB 정상 (status=UP, db=ok)")
    @ApiResponse(responseCode = "503", description = "DB 장애 (status=DOWN, db=fail)")
    public ResponseEntity<Map<String, Object>> deepHealth() {
        long startNanos = System.nanoTime();
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "UP");
            response.put("db", "ok");
            response.put("dbLatencyMs", (int) latencyMs);
            response.put("checkedAt", Instant.now().toString());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 인증 없는 공개 엔드포인트 — 예외 메시지/스택/접속 문자열은 절대 바디에 넣지 않는다
            log.warn("Deep health check failed: {}", e.getClass().getSimpleName());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "DOWN");
            response.put("db", "fail");
            response.put("checkedAt", Instant.now().toString());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
}
