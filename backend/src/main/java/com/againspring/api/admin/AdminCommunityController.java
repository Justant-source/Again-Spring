package com.againspring.api.admin;

import com.againspring.domain.community.CommunityReport;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostStatus;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.PostRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * DEPRECATED: 관리자 커뮤니티 운영
 *
 * 이 컨트롤러는 다음으로 분리되었습니다:
 * - AdminReportController: /api/admin/reports (신고 처리)
 * - AdminContentController: /api/admin/content (포스트/댓글 관리)
 *
 * 하위 호환성을 위해 유지되지만, 새 코드는 위의 분리된 컨트롤러를 사용하세요.
 */
@Deprecated(since = "2026-06-05", forRemoval = true)
@RestController
@RequestMapping("/api/admin/community")
@RequiredArgsConstructor
@Tag(name = "Admin — Community (Deprecated)", description = "커뮤니티 신고 처리·포스트 관리 (DEPRECATED → AdminReportController, AdminContentController 사용)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminCommunityController {

    private final CommunityReportRepository communityReportRepository;
    private final PostRepository postRepository;

    /**
     * GET /api/admin/community/reports?status=PENDING&page=0&size=20
     * 신고 목록 조회 (상태별, 페이지네이션)
     */
    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "신고 목록 조회",
        description = "PENDING 상태의 신고를 페이지 단위로 반환. 생성순 역순 정렬."
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
     * POST /api/admin/community/reports/{id}/resolve
     * 신고 처리 (차단/무시)
     */
    @PostMapping("/reports/{id}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "신고 처리",
        description = "신고에 대해 조치(BLOCK_POST/BLOCK_COMMENT/DISMISS)를 취한 후 상태를 RESOLVED로 변경"
    )
    @ApiResponse(responseCode = "200", description = "신고 처리 완료")
    @ApiResponse(responseCode = "404", description = "신고 또는 대상 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Void> resolveReport(
        @PathVariable Long id,
        @RequestBody ResolveRequest req) {

        CommunityReport report = communityReportRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("REPORT_NOT_FOUND"));

        // 신고 대상 조치 처리
        if ("BLOCK_POST".equals(req.getAction())) {
            postRepository.findById(report.getTargetId()).ifPresent(post -> {
                post.setStatus(PostStatus.BLOCKED);
                postRepository.save(post);
            });
        } else if ("BLOCK_COMMENT".equals(req.getAction())) {
            // TODO: 향후 댓글 차단 로직 추가
            // commentRepository.findById(...).ifPresent(comment -> {
            //     comment.setStatus(CommentStatus.BLOCKED);
            //     commentRepository.save(comment);
            // });
        }
        // DISMISS의 경우 대상에 대한 조치 없음, 신고만 RESOLVED 처리

        // 신고 상태 업데이트
        report.setStatus("RESOLVED");
        report.setResolvedAt(java.time.Instant.now());
        communityReportRepository.save(report);

        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/community/posts/{postId}/block
     * 포스트 차단 (관리자 직접 조치)
     */
    @PostMapping("/posts/{postId}/block")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "포스트 차단",
        description = "부적절한 포스트를 BLOCKED 상태로 변경"
    )
    @ApiResponse(responseCode = "200", description = "포스트 차단 완료")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Void> blockPost(@PathVariable String postId) {
        postRepository.findById(postId).ifPresent(post -> {
            post.setStatus(PostStatus.BLOCKED);
            postRepository.save(post);
        });
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/community/posts/{postId}/unblock
     * 포스트 차단 해제 (관리자 직접 조치)
     */
    @PostMapping("/posts/{postId}/unblock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "포스트 차단 해제",
        description = "차단된 포스트를 원래 상태로 복구 (기본값: VOTING)"
    )
    @ApiResponse(responseCode = "200", description = "포스트 차단 해제 완료")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Void> unblockPost(@PathVariable String postId) {
        postRepository.findById(postId).ifPresent(post -> {
            post.setStatus(PostStatus.VOTING);
            postRepository.save(post);
        });
        return ResponseEntity.ok().build();
    }

    /**
     * 신고 처리 요청 DTO
     */
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
}
