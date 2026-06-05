package com.againspring.api.admin;

import com.againspring.api.dto.response.AdminAuditLogResponse;
import com.againspring.domain.audit.AdminAuditLog;
import com.againspring.repository.audit.AdminAuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자 감사 로그 조회 API (V68)
 * ADMIN 권한 전용 — 관리자의 모든 작업 기록 조회
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@Tag(name = "Admin — Audit Logs", description = "감사 로그 조회 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminAuditController {

    private final AdminAuditLogRepository adminAuditLogRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "감사 로그 목록 조회 (페이지네이션 지원)")
    public ResponseEntity<Page<AdminAuditLogResponse>> listAuditLogs(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            Pageable pageable) {

        Page<AdminAuditLog> page;

        // 필터 조합에 따라 쿼리 실행
        if ((actor != null && !actor.isEmpty()) || (action != null && !action.isEmpty())) {
            // actor 또는 action으로 검색
            String actorFilter = (actor != null && !actor.isEmpty()) ? actor : "";
            String actionFilter = (action != null && !action.isEmpty()) ? action : "";
            page = adminAuditLogRepository.findByActorUserIdOrActionContaining(
                    actorFilter, actionFilter, pageable);
        } else if (targetType != null && !targetType.isEmpty()) {
            // targetType으로 필터링 (리포지토리 메서드 추가 필요시)
            // 현재는 모든 로그 반환 후 클라이언트 필터링
            page = adminAuditLogRepository.findAll(pageable);
        } else {
            // 모든 감사 로그
            page = adminAuditLogRepository.findAll(pageable);
        }

        // Page<AdminAuditLog> → Page<AdminAuditLogResponse> 변환
        List<AdminAuditLogResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        Page<AdminAuditLogResponse> responsePage = new PageImpl<>(
                content,
                pageable,
                page.getTotalElements()
        );

        return ResponseEntity.ok(responsePage);
    }

    /**
     * AdminAuditLog 엔티티를 Response DTO로 변환
     */
    private AdminAuditLogResponse toResponse(AdminAuditLog log) {
        return AdminAuditLogResponse.builder()
                .id(log.getId())
                .actorUserId(log.getActorUserId())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .beforeJson(log.getBeforeJson())
                .afterJson(log.getAfterJson())
                .ip(log.getIp())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
