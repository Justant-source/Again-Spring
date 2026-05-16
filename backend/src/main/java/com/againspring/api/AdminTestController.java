package com.againspring.api;

import com.againspring.service.AdminTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/test")
@Profile("dev")
@RequiredArgsConstructor
@Tag(name = "Admin — Test", description = "테스트 데이터 조작 (@Profile(dev) 전용)")
public class AdminTestController {

    private final AdminTestService adminTestService;

    @PostMapping("/reset")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "테스트 데이터 초기화", description = "테스트 사용자 데이터를 삭제한다. dev 프로파일 전용.")
    @ApiResponse(responseCode = "200", description = "삭제 건수 반환")
    @ApiResponse(responseCode = "403", description = "dev 환경 아님")
    public ResponseEntity<Map<String, Integer>> resetTestData() {
        log.info("Test data reset requested");
        Map<String, Integer> deleted = adminTestService.resetTestUserData();
        log.info("Test data reset complete: {}", deleted);
        return ResponseEntity.ok(deleted);
    }

    @PostMapping("/sessions/{sessionId}/terminate")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "세션 강제 종료", description = "지정 세션을 강제로 종료한다. dev 프로파일 전용.")
    @ApiResponse(responseCode = "200", description = "세션 종료 완료")
    public ResponseEntity<Void> terminateSession(@PathVariable String sessionId) {
        adminTestService.terminateSession(sessionId);
        return ResponseEntity.ok().build();
    }
}
