package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.domain.enums.PostStatus;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiCorrectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 4: 관리자 커뮤니티 콘텐츠 관리
 * 포스트·댓글 조회, 수정, 삭제, 차단
 */
@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
@Tag(name = "Admin — Content", description = "포스트/댓글 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContentController {

    private final PostRepository postRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final AiCorrectionService aiCorrectionService;

    // ===== 포스트 관리 =====

    /**
     * GET /api/admin/content/posts?status=VOTING&page=0&size=20
     * 포스트 목록 조회 (상태별, 페이지네이션)
     */
    @GetMapping("/posts")
    @Operation(
        summary = "포스트 목록 조회",
        description = "삭제되지 않은 전체 포스트를 조회. synthetic=true/false 로 AI/일반 유저 필터 가능 (ADMIN 전용)."
    )
    @ApiResponse(responseCode = "200", description = "포스트 목록 페이지")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Page<AdminPostView>> getPosts(
            @RequestParam(value = "synthetic", required = false) Boolean syntheticFilter,
            Pageable pageable) {

        Page<Post> posts;
        if (syntheticFilter == null) {
            posts = postRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable);
        } else {
            Set<String> allSyntheticIds = userRepository.findAllSyntheticIds();
            if (syntheticFilter) {
                posts = allSyntheticIds.isEmpty()
                        ? org.springframework.data.domain.Page.empty(pageable)
                        : postRepository.findByAuthorIdInAndDeletedAtIsNullOrderByCreatedAtDesc(allSyntheticIds, pageable);
            } else {
                posts = allSyntheticIds.isEmpty()
                        ? postRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable)
                        : postRepository.findByAuthorIdNotInAndDeletedAtIsNull(allSyntheticIds, pageable);
            }
        }

        // authorId 배치 조회로 synthetic 판별 (ADMIN 전용 — 공개 API 미노출)
        Set<String> authorIds = posts.getContent().stream()
                .map(Post::getAuthorId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<String> syntheticIds = authorIds.isEmpty()
                ? Set.of()
                : userRepository.findSyntheticIds(authorIds);

        return ResponseEntity.ok(posts.map(p ->
                new AdminPostView(p, syntheticIds.contains(p.getAuthorId()))));
    }

    /**
     * GET /api/admin/content/posts/{postId}
     * 단일 포스트 조회
     */
    @GetMapping("/posts/{postId}")
    @Operation(
        summary = "포스트 상세 조회",
        description = "포스트ID로 상세 정보 조회"
    )
    @ApiResponse(responseCode = "200", description = "포스트 상세 정보")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    public ResponseEntity<Post> getPost(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
        return ResponseEntity.ok(post);
    }

    /**
     * PATCH /api/admin/content/posts/{postId}
     * 포스트 수정 (제목, 본문, 상태, 카테고리)
     */
    @PatchMapping("/posts/{postId}")
    @Operation(
        summary = "포스트 수정",
        description = "포스트의 제목, 본문, 상태, 카테고리를 수정"
    )
    @ApiResponse(responseCode = "200", description = "포스트 수정 완료")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @Auditable(action = "POST_UPDATE", targetType = "POST", targetId = "#postId")
    public ResponseEntity<Post> updatePost(
            @PathVariable String postId,
            @RequestBody UpdatePostRequest req,
            org.springframework.security.core.Authentication auth) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        // 본문이 변경될 때 원본 저장 → 학습 데이터 후보로 캡처
        String originalBody = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();

        if (req.getTitle() != null) {
            post.setTitle(req.getTitle());
        }
        if (req.getBodyRaw() != null) {
            post.setBodyRaw(req.getBodyRaw());
            // 관리자 수정은 tonalization 없이 즉시 반영
            post.setBodyPublished(req.getBodyRaw());
        }
        if (req.getPartnerBodyRaw() != null) {
            post.setPartnerBodyRaw(req.getPartnerBodyRaw());
            post.setPartnerBodyPublished(req.getPartnerBodyRaw());
        }
        if (req.getStatus() != null) {
            post.setStatus(PostStatus.valueOf(req.getStatus()));
        }
        if (req.getCategory() != null) {
            post.setCategory(com.againspring.domain.enums.PostCategory.valueOf(req.getCategory()));
        }

        Post updated = postRepository.save(post);

        // 본문(bodyRaw) 변경 시 학습 데이터 후보로 PENDING 상태 저장
        if (req.getBodyRaw() != null && !req.getBodyRaw().equals(originalBody)) {
            try {
                aiCorrectionService.captureEdit("POST", postId, originalBody, req.getBodyRaw(),
                        auth != null ? auth.getName() : "admin");
            } catch (Exception e) {
                // 학습 데이터 캡처 실패는 수정 자체에 영향 없음
            }
        }

        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/admin/content/posts/{postId}
     * 포스트 소프트 삭제
     */
    @DeleteMapping("/posts/{postId}")
    @Operation(
        summary = "포스트 삭제",
        description = "포스트를 소프트 삭제 (deleted_at, deleted_by_admin_id 설정)"
    )
    @ApiResponse(responseCode = "204", description = "포스트 삭제 완료")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @Auditable(action = "POST_DELETE", targetType = "POST", targetId = "#postId")
    public ResponseEntity<Void> deletePost(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        // 현재 사용자 ID는 별도로 가져와야 함 (AOP에서 처리 시 시간이 늦음)
        String adminId = getAdminId();
        post.setDeletedAt(Instant.now());
        post.setDeletedByAdminId(adminId);

        postRepository.save(post);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/content/posts/{postId}/block
     * 포스트 차단 (상태를 BLOCKED로 변경)
     */
    @PostMapping("/posts/{postId}/block")
    @Operation(
        summary = "포스트 차단",
        description = "부적절한 포스트를 BLOCKED 상태로 변경"
    )
    @ApiResponse(responseCode = "200", description = "포스트 차단 완료")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @Auditable(action = "POST_BLOCK", targetType = "POST", targetId = "#postId")
    public ResponseEntity<Void> blockPost(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        post.setStatus(PostStatus.BLOCKED);
        postRepository.save(post);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/content/posts/{postId}/unblock
     * 포스트 차단 해제 (상태를 VOTING으로 복구)
     */
    @PostMapping("/posts/{postId}/unblock")
    @Operation(
        summary = "포스트 차단 해제",
        description = "차단된 포스트를 VOTING 상태로 복구"
    )
    @ApiResponse(responseCode = "200", description = "포스트 차단 해제 완료")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @Auditable(action = "POST_UNBLOCK", targetType = "POST", targetId = "#postId")
    public ResponseEntity<Void> unblockPost(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        post.setStatus(PostStatus.VOTING);
        postRepository.save(post);
        return ResponseEntity.ok().build();
    }

    // ===== 댓글 관리 =====

    /**
     * GET /api/admin/content/comments?status=ACTIVE&page=0&size=20
     * 댓글 목록 조회 (상태별, 페이지네이션)
     */
    @GetMapping("/comments")
    @Operation(
        summary = "댓글 목록 조회",
        description = "상태별로 댓글을 조회 (관리자용, 모든 상태 포함). synthetic 필드로 AI 봇 작성 여부 확인 가능 (ADMIN 전용)."
    )
    @ApiResponse(responseCode = "200", description = "댓글 목록 페이지")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Page<AdminCommentView>> getComments(
            @RequestParam(value = "status", defaultValue = "ACTIVE") String status,
            Pageable pageable) {

        CommentStatus commentStatus = CommentStatus.valueOf(status.toUpperCase());
        Page<PostComment> comments = postCommentRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                commentStatus, pageable);

        // authorId 배치 조회로 synthetic 판별 (ADMIN 전용 — 공개 API 미노출)
        Set<String> authorIds = comments.getContent().stream()
                .map(PostComment::getAuthorId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Set<String> syntheticIds = authorIds.isEmpty()
                ? Set.of()
                : userRepository.findSyntheticIds(authorIds);

        return ResponseEntity.ok(comments.map(c ->
                new AdminCommentView(c, syntheticIds.contains(c.getAuthorId()))));
    }

    /**
     * PATCH /api/admin/content/comments/{commentId}
     * 댓글 수정 (본문)
     */
    @PatchMapping("/comments/{commentId}")
    @Operation(
        summary = "댓글 수정",
        description = "댓글 본문을 수정"
    )
    @ApiResponse(responseCode = "200", description = "댓글 수정 완료")
    @ApiResponse(responseCode = "404", description = "댓글 없음")
    @Auditable(action = "COMMENT_UPDATE", targetType = "COMMENT", targetId = "#commentId")
    public ResponseEntity<PostComment> updateComment(
            @PathVariable Long commentId,
            @RequestBody UpdateCommentRequest req,
            org.springframework.security.core.Authentication auth) {

        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        String originalBody = comment.getBody();

        if (req.getBody() != null) {
            comment.setBody(req.getBody());
        }

        PostComment updated = postCommentRepository.save(comment);

        // 본문 변경 시 학습 데이터 후보로 PENDING 상태 저장
        if (req.getBody() != null && !req.getBody().equals(originalBody)) {
            try {
                aiCorrectionService.captureEdit("COMMENT", String.valueOf(commentId),
                        originalBody, req.getBody(),
                        auth != null ? auth.getName() : "admin");
            } catch (Exception e) {
                // 학습 데이터 캡처 실패는 수정 자체에 영향 없음
            }
        }

        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/admin/content/comments/{commentId}
     * 댓글 소프트 삭제
     */
    @DeleteMapping("/comments/{commentId}")
    @Operation(
        summary = "댓글 삭제",
        description = "댓글을 소프트 삭제 (deleted_at, deleted_by_admin_id 설정)"
    )
    @ApiResponse(responseCode = "204", description = "댓글 삭제 완료")
    @ApiResponse(responseCode = "404", description = "댓글 없음")
    @Auditable(action = "COMMENT_DELETE", targetType = "COMMENT", targetId = "#commentId")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        String adminId = getAdminId();
        comment.setDeletedAt(Instant.now());
        comment.setDeletedByAdminId(adminId);

        postCommentRepository.save(comment);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/content/comments/{commentId}/block
     * 댓글 차단 (상태를 BLOCKED로 변경)
     */
    @PostMapping("/comments/{commentId}/block")
    @Operation(
        summary = "댓글 차단",
        description = "부적절한 댓글을 BLOCKED 상태로 변경"
    )
    @ApiResponse(responseCode = "200", description = "댓글 차단 완료")
    @ApiResponse(responseCode = "404", description = "댓글 없음")
    @Auditable(action = "COMMENT_BLOCK", targetType = "COMMENT", targetId = "#commentId")
    public ResponseEntity<Void> blockComment(@PathVariable Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        comment.setStatus(CommentStatus.BLOCKED);
        postCommentRepository.save(comment);
        return ResponseEntity.ok().build();
    }

    /**
     * POST /api/admin/content/comments/{commentId}/unblock
     * 댓글 차단 해제 (상태를 ACTIVE로 변경)
     */
    @PostMapping("/comments/{commentId}/unblock")
    @Operation(
        summary = "댓글 차단 해제",
        description = "차단된 댓글을 ACTIVE 상태로 복구"
    )
    @ApiResponse(responseCode = "200", description = "댓글 차단 해제 완료")
    @ApiResponse(responseCode = "404", description = "댓글 없음")
    @Auditable(action = "COMMENT_UNBLOCK", targetType = "COMMENT", targetId = "#commentId")
    public ResponseEntity<Void> unblockComment(@PathVariable Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        comment.setStatus(CommentStatus.ACTIVE);
        postCommentRepository.save(comment);
        return ResponseEntity.ok().build();
    }

    // ===== Helper Methods =====

    /**
     * 현재 인증된 관리자 ID 가져오기
     */
    private String getAdminId() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    // ===== View DTOs (ADMIN 전용 응답) =====

    /**
     * Post + synthetic 플래그 래퍼.
     * @JsonUnwrapped로 Post 필드를 평탄화해 기존 FE AdminPost 타입과 호환.
     * synthetic은 ADMIN 전용 — 공개 API 응답에 절대 포함하지 않는다.
     */
    @Getter
    public static class AdminPostView {
        @JsonUnwrapped
        private final Post post;
        /** AI 봇 작성 여부 (users.synthetic=1). ADMIN 전용. */
        private final boolean synthetic;

        public AdminPostView(Post post, boolean synthetic) {
            this.post = post;
            this.synthetic = synthetic;
        }
    }

    /**
     * PostComment + synthetic 플래그 래퍼.
     * @JsonUnwrapped로 기존 FE AdminComment 타입과 호환.
     */
    @Getter
    public static class AdminCommentView {
        @JsonUnwrapped
        private final PostComment comment;
        /** AI 봇 작성 여부 (users.synthetic=1). ADMIN 전용. */
        private final boolean synthetic;

        public AdminCommentView(PostComment comment, boolean synthetic) {
            this.comment = comment;
            this.synthetic = synthetic;
        }
    }

    // ===== Request DTOs =====

    @Getter
    @Setter
    public static class UpdatePostRequest {
        private String title;
        private String bodyRaw;
        private String partnerBodyRaw;
        private String status;
        private String category;
    }

    @Getter
    @Setter
    public static class UpdateCommentRequest {
        private String body;
    }
}
