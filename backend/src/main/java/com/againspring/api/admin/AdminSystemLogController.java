package com.againspring.api.admin;

import com.againspring.config.LogBufferAppender;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system/logs")
@Tag(name = "Admin — System Logs", description = "애플리케이션 ERROR/WARN 로그 조회 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminSystemLogController {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "최근 ERROR/WARN 로그 조회",
               description = "level=ERROR|WARN 필터 가능. limit 기본 100, 최대 500.")
    public ResponseEntity<List<LogBufferAppender.LogEntry>> getLogs(
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "100") int limit) {
        int capped = Math.min(limit, 500);
        return ResponseEntity.ok(LogBufferAppender.getEntries(level, capped));
    }
}
