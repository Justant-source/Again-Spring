package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.dto.request.CreateAnnouncementRequest;
import com.againspring.domain.announcement.Announcement;
import com.againspring.service.admin.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 공지사항 관리 API
 * 공지사항 CRUD + 활성화 + 알림 발송
 */
@RestController
@RequestMapping("/api/admin/announcements")
@RequiredArgsConstructor
@Tag(name = "Admin — Announcements", description = "공지사항 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /**
     * GET /api/admin/announcements?page=0&size=20&isActive=true
     * 공지사항 목록 조회
     */
    @GetMapping
    @Operation(
        summary = "공지사항 목록 조회",
        description = "페이지네이션과 함께 공지사항 목록을 반환 (최신순)"
    )
    @ApiResponse(responseCode = "200", description = "공지사항 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Page<Announcement>> getAnnouncements(
            @RequestParam(required = false) Boolean isActive,
            Pageable pageable) {

        Page<Announcement> announcements = announcementService.getAnnouncements(pageable, isActive);
        return ResponseEntity.ok(announcements);
    }

    /**
     * GET /api/admin/announcements/{id}
     * 공지사항 상세 조회
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "공지사항 상세 조회",
        description = "특정 ID의 공지사항을 반환"
    )
    @ApiResponse(responseCode = "200", description = "공지사항 상세 정보")
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Announcement> getAnnouncement(@PathVariable String id) {

        Announcement announcement = announcementService.getAnnouncement(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다"));

        return ResponseEntity.ok(announcement);
    }

    /**
     * POST /api/admin/announcements
     * 공지사항 작성
     */
    @PostMapping
    @Operation(
        summary = "공지사항 작성",
        description = "새로운 공지사항을 생성 (초기 상태: 비활성)"
    )
    @ApiResponse(responseCode = "201", description = "공지사항 생성 완료", content = @Content(schema = @Schema(implementation = Announcement.class)))
    @ApiResponse(responseCode = "400", description = "요청 데이터 오류")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "ANNOUNCEMENT_CREATE")
    public ResponseEntity<Announcement> createAnnouncement(
            @RequestBody CreateAnnouncementRequest request,
            Authentication authentication) {

        String createdBy = authentication.getName();
        Announcement announcement = announcementService.createAnnouncement(request, createdBy);

        return ResponseEntity.status(HttpStatus.CREATED).body(announcement);
    }

    /**
     * PATCH /api/admin/announcements/{id}
     * 공지사항 수정
     */
    @PatchMapping("/{id}")
    @Operation(
        summary = "공지사항 수정",
        description = "공지사항의 제목, 본문, 시간 설정을 수정"
    )
    @ApiResponse(responseCode = "200", description = "공지사항 수정 완료")
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "ANNOUNCEMENT_UPDATE", targetType = "ANNOUNCEMENT", targetId = "#id")
    public ResponseEntity<Announcement> updateAnnouncement(
            @PathVariable String id,
            @RequestBody CreateAnnouncementRequest request) {

        Announcement announcement = announcementService.updateAnnouncement(id, request);
        return ResponseEntity.ok(announcement);
    }

    /**
     * DELETE /api/admin/announcements/{id}
     * 공지사항 삭제
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "공지사항 삭제",
        description = "공지사항을 삭제"
    )
    @ApiResponse(responseCode = "204", description = "공지사항 삭제 완료")
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "ANNOUNCEMENT_DELETE", targetType = "ANNOUNCEMENT", targetId = "#id")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable String id) {

        announcementService.deleteAnnouncement(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/announcements/{id}/activate
     * 공지사항 활성화
     */
    @PostMapping("/{id}/activate")
    @Operation(
        summary = "공지사항 활성화",
        description = "공지사항을 활성화 상태로 변경"
    )
    @ApiResponse(responseCode = "204", description = "활성화 완료")
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "ANNOUNCEMENT_ACTIVATE", targetType = "ANNOUNCEMENT", targetId = "#id")
    public ResponseEntity<Void> activateAnnouncement(@PathVariable String id) {

        announcementService.activateAnnouncement(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/announcements/{id}/deactivate
     * 공지사항 비활성화
     */
    @PostMapping("/{id}/deactivate")
    @Operation(
        summary = "공지사항 비활성화",
        description = "공지사항을 비활성화 상태로 변경"
    )
    @ApiResponse(responseCode = "204", description = "비활성화 완료")
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "ANNOUNCEMENT_DEACTIVATE", targetType = "ANNOUNCEMENT", targetId = "#id")
    public ResponseEntity<Void> deactivateAnnouncement(@PathVariable String id) {

        announcementService.deactivateAnnouncement(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/announcements/{id}/notify
     * 공지사항에 대한 알림 발송 (모든 활성 사용자에게)
     */
    @PostMapping("/{id}/notify")
    @Operation(
        summary = "공지사항 알림 발송",
        description = "모든 활성 비게스트 사용자에게 공지사항 알림을 발송"
    )
    @ApiResponse(responseCode = "204", description = "알림 발송 완료")
    @ApiResponse(responseCode = "404", description = "공지사항 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "ANNOUNCEMENT_NOTIFY", targetType = "ANNOUNCEMENT", targetId = "#id")
    public ResponseEntity<Void> notifyAnnouncement(@PathVariable String id) {

        announcementService.notifyAnnouncement(id);
        return ResponseEntity.noContent().build();
    }
}
