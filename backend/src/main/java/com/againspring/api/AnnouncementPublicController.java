package com.againspring.api;

import com.againspring.domain.announcement.Announcement;
import com.againspring.repository.announcement.AnnouncementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * 공개 공지사항 조회 API
 * 인증 불필요, 활성 공지사항만 조회
 */
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@Tag(name = "Announcements (Public)", description = "공지사항 공개 조회 (인증 불필요)")
public class AnnouncementPublicController {

    private final AnnouncementRepository announcementRepository;

    /**
     * GET /api/announcements/active
     * 활성 공지사항 조회 (현재 시간 범위 내)
     * startsAt이 null이거나 과거, endsAt이 null이거나 미래인 공지사항만 반환
     */
    @GetMapping("/active")
    @Operation(
        summary = "활성 공지사항 조회",
        description = "현재 활성화된 공지사항을 시간 범위와 무관하게 조회 (게시판용)"
    )
    @ApiResponse(responseCode = "200", description = "활성 공지사항 목록")
    public ResponseEntity<List<Announcement>> getActiveAnnouncements() {

        Instant now = Instant.now();

        // startsAt이 null이거나 과거이고, endsAt이 null이거나 미래인 공지사항
        List<Announcement> announcements = announcementRepository.findByIsActiveTrueOrderByCreatedAtDesc()
            .stream()
            .filter(a -> {
                // startsAt 체크: null이거나 현재 이전
                boolean startsOk = a.getStartsAt() == null || !a.getStartsAt().isAfter(now);
                // endsAt 체크: null이거나 현재 이후
                boolean endsOk = a.getEndsAt() == null || !a.getEndsAt().isBefore(now);
                return startsOk && endsOk;
            })
            .toList();

        return ResponseEntity.ok(announcements);
    }
}
