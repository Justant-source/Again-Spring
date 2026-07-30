package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.domain.community.CommunityReport;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.domain.enums.PostStatus;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiUserOutboxWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Phase 4: 관리자 신고 처리
 * 사용자 신고 큐 조회 및 처리 (차단/무시)
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin — Reports", description = "신고 처리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final CommunityReportRepository communityReportRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostRepository postRepository;
    private final AiUserOutboxWriter aiUserOutboxWriter;

    /**
     * GET /api/admin/reports?status=PENDING&page=0&size=20
     * 신고 목록 조회 (상태별, 페이지네이션)
     */
    @GetMapping
    @Operation(
        summary = "신고 목록 조회",
        description = "상태별로 신고를 페이지 단위로 반환 (생성순 역순 정렬)"
    )
    @ApiResponse(responseCode = "200", description = "신고 목록 페이지")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Page<CommunityReport>> getReports(
            @RequestParam(value = "status", defaultValue = "PENDING") String status,
            Pageable pageable) {

        Page<CommunityReport> reports = communityReportRepository
                .findByStatusOrderByCreatedAtDesc(status, pageable);

        return ResponseEntity.ok(reports);
    }

    /**
     * GET /api/admin/reports/count?status=PENDING
     * 신고 개수 조회 (배지 폴링용)
     */
    @GetMapping("/count")
    @Operation(
        summary = "신고 개수 조회",
        description = "특정 상태의 신고 개수를 반환 (관리자 대시보드 배지용)"
    )
    @ApiResponse(responseCode = "200", description = "신고 개수")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<CountResponse> getReportCount(
            @RequestParam(value = "status", defaultValue = "PENDING") String status) {

        long count = communityReportRepository.countByStatus(status);
        return ResponseEntity.ok(new CountResponse(count));
    }

    /**
     * POST /api/admin/reports/{id}/resolve
     * 신고 처리 (차단/무시)
     */
    @PostMapping("/{id}/resolve")
    @Operation(
        summary = "신고 처리",
        description = "신고에 대해 조치(BLOCK_POST/BLOCK_COMMENT/DISMISS)를 취한 후 상태를 RESOLVED로 변경"
    )
    @ApiResponse(responseCode = "200", description = "신고 처리 완료")
    @ApiResponse(responseCode = "404", description = "신고 또는 대상 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @Auditable(action = "REPORT_RESOLVE", targetType = "REPORT", targetId = "#id")
    @Transactional
    public ResponseEntity<Void> resolveReport(
            @PathVariable Long id,
            @RequestBody ResolveRequest req) {

        CommunityReport report = communityReportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND"));

        // 신고 대상 조치 처리
        if ("BLOCK_POST".equals(req.getAction())) {
            Post post = postRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
            post.setStatus(PostStatus.BLOCKED);
            Post saved = postRepository.save(post);
            aiUserOutboxWriter.postLifecycleChanged(saved, "POST_BLOCKED", "REPORT_RESOLVED_BLOCK");
        } else if ("BLOCK_COMMENT".equals(req.getAction())) {
            // 댓글 차단 처리
            try {
                Long commentId = Long.valueOf(report.getTargetId());
                PostComment comment = postCommentRepository.findById(commentId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

                comment.setStatus(CommentStatus.BLOCKED);
                PostComment saved = postCommentRepository.save(comment);
                Post post = postRepository.findById(saved.getPostId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
                aiUserOutboxWriter.commentLifecycleChanged(post, saved,
                        saved.getParentCommentId() == null ? "COMMENT_BLOCKED" : "REPLY_BLOCKED", "REPORT_RESOLVED_BLOCK");
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_ID");
            }
        }
        // DISMISS의 경우 대상에 대한 조치 없음, 신고만 RESOLVED 처리

        // 신고 상태 업데이트
        report.setStatus("RESOLVED");
        report.setResolvedAt(Instant.now());
        communityReportRepository.save(report);

        return ResponseEntity.ok().build();
    }

    // ===== Request/Response DTOs =====

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    public static class ResolveRequest {
        /**
         * 처리 액션
         * - "BLOCK_POST": 포스트 차단
         * - "BLOCK_COMMENT": 댓글 차단
         * - "DISMISS": 신고 무시 (조치 없음)
         */
        private String action;

        /** 선택사항: 처리 사유 */
        private String reason;
    }

    @Getter
    @AllArgsConstructor
    public static class CountResponse {
        private long count;
    }
}
