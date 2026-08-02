package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.PostLike;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostCategory;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostLikeRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiCorrectionService;
import com.againspring.service.ai.AiUserOutboxWriter;
import com.againspring.service.admin.AdminPublishedThreadService;
import com.againspring.service.admin.AdminPublishedThreadService.UpdateThreadRequest;
import com.againspring.service.community.PostSearchNgramIndexer;
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
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final AiCorrectionService aiCorrectionService;
    private final AiUserOutboxWriter aiUserOutboxWriter;
    private final AdminPublishedThreadService publishedThreadService;
    private final com.againspring.service.community.PromoTitleService promoTitleService;
    private final PostSearchNgramIndexer postSearchNgramIndexer;

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

        List<String> postIds = posts.getContent().stream().map(Post::getId).toList();
        Map<String, Long> commentCounts = publishedThreadService.commentCountsFor(postIds);

        return ResponseEntity.ok(posts.map(p ->
                new AdminPostView(p, syntheticIds.contains(p.getAuthorId()),
                        commentCounts.getOrDefault(p.getId(), 0L))));
    }

    /**
     * GET /api/admin/content/posts/{postId}/thread
     * 글 + 댓글/대댓글 타임라인 (관리자 스레드 편집기)
     */
    @GetMapping("/posts/{postId}/thread")
    @Operation(summary = "게시글 스레드 조회", description = "글과 미삭제 댓글/대댓글을 타임라인으로 반환 (ADMIN 전용).")
    @ApiResponse(responseCode = "200", description = "스레드")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    public ResponseEntity<Map<String, Object>> getThread(@PathVariable String postId) {
        return ResponseEntity.ok(publishedThreadService.getThread(postId));
    }

    /**
     * PATCH /api/admin/content/posts/{postId}/thread
     * 글·댓글 본문/작성자/createdAt 일괄 수정. items에서 빠진 댓글은 soft-delete.
     */
    @PatchMapping("/posts/{postId}/thread")
    @Operation(summary = "게시글 스레드 수정", description = "예약 홀딩과 동일한 프레임용 일괄 저장.")
    @ApiResponse(responseCode = "200", description = "수정 완료")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @Auditable(action = "POST_THREAD_UPDATE", targetType = "POST", targetId = "#postId")
    public ResponseEntity<Map<String, Object>> patchThread(
            @PathVariable String postId,
            @RequestBody UpdateThreadRequest req,
            org.springframework.security.core.Authentication auth) {
        String adminId = auth != null ? auth.getName() : getAdminId();
        return ResponseEntity.ok(publishedThreadService.patchThread(postId, req == null ? new UpdateThreadRequest() : req, adminId));
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
     * GET /api/admin/content/posts/{postId}/source-comparison
     * 원본 비교 화면 데이터 — 왼쪽(원본) + 오른쪽(생성본) 정보를 한 번에 반환.
     * hasSource=false이면 원본 정보 없는 일반(창작) 사연.
     */
    @GetMapping("/posts/{postId}/source-comparison")
    @Operation(
        summary = "원본 비교 데이터 조회",
        description = "재구성 사연의 크롤 원본(왼쪽)과 생성본(오른쪽) 정보를 반환"
    )
    @ApiResponse(responseCode = "200", description = "비교 데이터")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    public ResponseEntity<SourceComparisonResponse> getSourceComparison(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
        Set<String> syntheticIds = userRepository.findSyntheticIds(java.util.List.of(post.getAuthorId()));
        boolean synthetic = syntheticIds.contains(post.getAuthorId());
        boolean hasSource = post.getSourceExampleId() != null;

        SourceComparisonResponse.SourceData source = hasSource
            ? new SourceComparisonResponse.SourceData(
                post.getSourceExampleId(),
                post.getSourceCommunity(),
                post.getSourceUrl(),
                post.getSourceOriginalTitle(),
                post.getSourceOriginalBody())
            : null;
        SourceComparisonResponse.GeneratedData generated =
            new SourceComparisonResponse.GeneratedData(post.getTitle(), post.getBodyPublished());

        return ResponseEntity.ok(new SourceComparisonResponse(synthetic, hasSource, source, generated));
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
    @Transactional
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
            post.setUserTitle(req.getTitle());
        }
        boolean contentChanged = false;
        if (req.getBodyRaw() != null) {
            post.setBodyRaw(req.getBodyRaw());
            // 관리자 수정은 tonalization 없이 즉시 반영
            post.setBodyPublished(req.getBodyRaw());
            contentChanged = !req.getBodyRaw().equals(originalBody);
        }
        if (req.getPartnerBodyRaw() != null) {
            post.setPartnerBodyRaw(req.getPartnerBodyRaw());
            post.setPartnerBodyPublished(req.getPartnerBodyRaw());
            contentChanged = true;
        }
        if (req.getStatus() != null) {
            post.setStatus(PostStatus.valueOf(req.getStatus()));
        }
        if (req.getCategory() != null) {
            post.setCategory(com.againspring.domain.enums.PostCategory.valueOf(req.getCategory()));
        }
        if (req.getViewCount() != null) {
            post.setViewCount(req.getViewCount());
        }

        if (contentChanged) {
            post.advanceContentRevision();
        }

        Post updated = postRepository.save(post);
        if (req.getTitle() != null || req.getBodyRaw() != null) {
            postSearchNgramIndexer.reindex(updated);
        }
        if (contentChanged) {
            aiUserOutboxWriter.postRevised(updated, "ADMIN_CONTENT_UPDATED");
        } else if (req.getStatus() != null) {
            String eventType = updated.getStatus() == PostStatus.BLOCKED ? "POST_BLOCKED" : "POST_STATUS_CHANGED";
            aiUserOutboxWriter.postLifecycleChanged(updated, eventType, "ADMIN_STATUS_UPDATED");
        }

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
    @Transactional
    public ResponseEntity<Void> deletePost(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        // 현재 사용자 ID는 별도로 가져와야 함 (AOP에서 처리 시 시간이 늦음)
        String adminId = getAdminId();
        post.setDeletedAt(Instant.now());
        post.setDeletedByAdminId(adminId);

        Post saved = postRepository.save(post);
        aiUserOutboxWriter.postLifecycleChanged(saved, "POST_DELETED", "ADMIN_DELETED");
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
    @Transactional
    public ResponseEntity<Void> blockPost(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        post.setStatus(PostStatus.BLOCKED);
        Post saved = postRepository.save(post);
        aiUserOutboxWriter.postLifecycleChanged(saved, "POST_BLOCKED", "ADMIN_BLOCKED");
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
    @Transactional
    public ResponseEntity<Void> unblockPost(@PathVariable String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        post.setStatus(PostStatus.VOTING);
        Post saved = postRepository.save(post);
        aiUserOutboxWriter.postLifecycleChanged(saved, "POST_UNBLOCKED", "ADMIN_UNBLOCKED");
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
    @Transactional
    public ResponseEntity<PostComment> updateComment(
            @PathVariable Long commentId,
            @RequestBody UpdateCommentRequest req,
            org.springframework.security.core.Authentication auth) {

        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        String originalBody = comment.getBody();

        boolean contentChanged = req.getBody() != null && !req.getBody().equals(originalBody);
        if (req.getBody() != null) {
            comment.setBody(req.getBody());
        }

        if (contentChanged) {
            comment.advanceContentRevision();
        }

        PostComment updated = postCommentRepository.save(comment);
        if (contentChanged) {
            Post post = postRepository.findById(updated.getPostId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
            aiUserOutboxWriter.commentUpdated(post, updated);
        }

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
    @Transactional
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        String adminId = getAdminId();
        comment.setDeletedAt(Instant.now());
        comment.setDeletedByAdminId(adminId);

        PostComment saved = postCommentRepository.save(comment);
        Post post = postRepository.findById(saved.getPostId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
        aiUserOutboxWriter.commentLifecycleChanged(post, saved,
                saved.getParentCommentId() == null ? "COMMENT_DELETED" : "REPLY_DELETED", "ADMIN_DELETED");
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
    @Transactional
    public ResponseEntity<Void> blockComment(@PathVariable Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        comment.setStatus(CommentStatus.BLOCKED);
        PostComment saved = postCommentRepository.save(comment);
        Post post = postRepository.findById(saved.getPostId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
        aiUserOutboxWriter.commentLifecycleChanged(post, saved,
                saved.getParentCommentId() == null ? "COMMENT_BLOCKED" : "REPLY_BLOCKED", "ADMIN_BLOCKED");
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
    @Transactional
    public ResponseEntity<Void> unblockComment(@PathVariable Long commentId) {
        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        comment.setStatus(CommentStatus.ACTIVE);
        PostComment saved = postCommentRepository.save(comment);
        Post post = postRepository.findById(saved.getPostId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));
        aiUserOutboxWriter.commentLifecycleChanged(post, saved,
                saved.getParentCommentId() == null ? "COMMENT_UNBLOCKED" : "REPLY_UNBLOCKED", "ADMIN_UNBLOCKED");
        return ResponseEntity.ok().build();
    }

    // ===== 좋아요 조정 =====

    /**
     * POST /api/admin/content/posts/{postId}/likes/adjust
     * 포스트 좋아요 개수 조정 (delta = ±1, AI 유저만)
     */
    @PostMapping("/posts/{postId}/likes/adjust")
    @Operation(
        summary = "포스트 좋아요 조정",
        description = "AI 유저만 선택해 좋아요 +1 또는 -1 (delta 필수: 1 또는 -1)"
    )
    @ApiResponse(responseCode = "200", description = "좋아요 개수")
    @ApiResponse(responseCode = "400", description = "delta 값 오류 또는 AI 유저 부족/초과")
    @ApiResponse(responseCode = "404", description = "포스트 없음")
    @Auditable(action = "POST_LIKE_ADJUST", targetType = "POST", targetId = "#postId")
    @Transactional
    public ResponseEntity<AdjustLikesResponse> adjustPostLikes(
            @PathVariable String postId,
            @RequestBody AdjustLikesRequest req) {

        if (req.getDelta() == null || (req.getDelta() != 1 && req.getDelta() != -1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delta must be 1 or -1");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND"));

        Set<String> syntheticIds = userRepository.findAllSyntheticIds();

        if (req.getDelta() == 1) {
            // delta=1: AI 유저 중 이 글에 아직 좋아요 안 누른 유저를 랜덤 선택
            List<String> candidates = syntheticIds.stream()
                    .filter(userId -> !postLikeRepository.existsByPostIdAndUserId(postId, userId))
                    .toList();

            if (candidates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No synthetic users available to add like");
            }

            // 랜덤 선택
            String selectedUserId = candidates.get((int)(Math.random() * candidates.size()));
            PostLike like = PostLike.builder()
                    .postId(postId)
                    .userId(selectedUserId)
                    .build();
            postLikeRepository.save(like);
        } else {
            // delta=-1: 이 글의 좋아요 중 AI 유저 소유 행을 우선 삭제
            // 실제 사람 유저의 좋아요는 절대 삭제하지 않음
            List<PostLike> syntheticLikes = postLikeRepository.findByPostIdAndUserIdIn(postId, syntheticIds);

            if (syntheticLikes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No synthetic user likes available to remove");
            }

            // 첫 번째 AI 유저 좋아요 삭제
            postLikeRepository.deleteById(syntheticLikes.get(0).getId());
        }

        Long likeCount = postLikeRepository.countByPostId(postId);
        return ResponseEntity.ok(new AdjustLikesResponse(likeCount != null ? likeCount : 0L));
    }

    /**
     * POST /api/admin/content/comments/{commentId}/likes/adjust
     * 댓글 좋아요 개수 조정 (delta = ±1, AI 유저만)
     */
    @PostMapping("/comments/{commentId}/likes/adjust")
    @Operation(
        summary = "댓글 좋아요 조정",
        description = "AI 유저만 선택해 좋아요 +1 또는 -1 (delta 필수: 1 또는 -1). likeCount 컬럼도 함께 갱신."
    )
    @ApiResponse(responseCode = "200", description = "좋아요 개수")
    @ApiResponse(responseCode = "400", description = "delta 값 오류 또는 AI 유저 부족/초과")
    @ApiResponse(responseCode = "404", description = "댓글 없음")
    @Auditable(action = "COMMENT_LIKE_ADJUST", targetType = "COMMENT", targetId = "#commentId")
    @Transactional
    public ResponseEntity<AdjustLikesResponse> adjustCommentLikes(
            @PathVariable Long commentId,
            @RequestBody AdjustLikesRequest req) {

        if (req.getDelta() == null || (req.getDelta() != 1 && req.getDelta() != -1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delta must be 1 or -1");
        }

        PostComment comment = postCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND"));

        Set<String> syntheticIds = userRepository.findAllSyntheticIds();
        Integer newLikeCount = comment.getLikeCount() != null ? comment.getLikeCount() : 0;

        if (req.getDelta() == 1) {
            // delta=1: AI 유저 중 이 댓글에 아직 좋아요 안 누른 유저를 랜덤 선택
            List<String> candidates = syntheticIds.stream()
                    .filter(userId -> !postLikeRepository.existsByCommentIdAndUserId(commentId, userId))
                    .toList();

            if (candidates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No synthetic users available to add like");
            }

            String selectedUserId = candidates.get((int)(Math.random() * candidates.size()));
            PostLike like = PostLike.builder()
                    .commentId(commentId)
                    .userId(selectedUserId)
                    .build();
            postLikeRepository.save(like);
            newLikeCount++;
        } else {
            // delta=-1: 이 댓글의 좋아요 중 AI 유저 소유 행을 우선 삭제
            List<PostLike> syntheticLikes = postLikeRepository.findByCommentIdAndUserIdIn(commentId, syntheticIds);

            if (syntheticLikes.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No synthetic user likes available to remove");
            }

            // 첫 번째 AI 유저 좋아요 삭제
            postLikeRepository.deleteById(syntheticLikes.get(0).getId());
            newLikeCount = Math.max(0, newLikeCount - 1);
        }

        // likeCount 컬럼도 함께 갱신해 동기화
        comment.setLikeCount(newLikeCount);
        postCommentRepository.save(comment);

        return ResponseEntity.ok(new AdjustLikesResponse(newLikeCount.longValue()));
    }

    // ===== 콘텐츠 생성 =====

    /**
     * POST /api/admin/content/posts
     * 어드민이 직접 게시글 생성
     */
    @PostMapping("/posts")
    @Operation(
        summary = "게시글 생성",
        description = "어드민이 직접 게시글을 생성 (createdByAdmin=true로 표시)"
    )
    @ApiResponse(responseCode = "201", description = "생성된 게시글")
    @ApiResponse(responseCode = "400", description = "필수 필드 누락")
    @Auditable(action = "POST_CREATE_ADMIN", targetType = "POST", targetId = "#result.id")
    @Transactional
    public ResponseEntity<AdminPostView> createPost(@RequestBody CreatePostRequest req) {
        if (req.getTitle() == null || req.getTitle().trim().isEmpty() ||
            req.getBodyRaw() == null || req.getBodyRaw().trim().isEmpty() ||
            req.getCategory() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "title, bodyRaw, category are required");
        }

        // ID 생성 (UUID 32자)
        String postId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        Post post = Post.builder()
                .id(postId)
                .authorId(req.getAuthorId() != null ? req.getAuthorId() : "admin")
                .title(req.getTitle())
                .userTitle(req.getTitle())
                .bodyRaw(req.getBodyRaw())
                .bodyPublished(req.getBodyRaw())
                .category(PostCategory.valueOf(req.getCategory()))
                .jurorCount(0)
                .status(PostStatus.VOTING)
                .visibility(com.againspring.domain.enums.PostVisibility.PUBLIC)
                .createdByAdmin(true)
                .build();

        Post saved = postRepository.save(post);
        postSearchNgramIndexer.reindex(saved);

        promoTitleService.generateAsync(saved.getId());

        Set<String> syntheticIds = userRepository.findSyntheticIds(java.util.List.of(saved.getAuthorId()));
        boolean synthetic = syntheticIds.contains(saved.getAuthorId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AdminPostView(saved, synthetic));
    }

    /**
     * POST /api/admin/content/comments
     * 어드민이 직접 댓글 생성
     */
    @PostMapping("/comments")
    @Operation(
        summary = "댓글 생성",
        description = "어드민이 직접 댓글을 생성 (createdByAdmin=true로 표시)"
    )
    @ApiResponse(responseCode = "201", description = "생성된 댓글")
    @ApiResponse(responseCode = "400", description = "필수 필드 누락 또는 포스트 없음")
    @Auditable(action = "COMMENT_CREATE_ADMIN", targetType = "COMMENT", targetId = "#result.id")
    @Transactional
    public ResponseEntity<AdminCommentView> createComment(@RequestBody CreateCommentRequest req) {
        if (req.getPostId() == null || req.getPostId().trim().isEmpty() ||
            req.getBody() == null || req.getBody().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "postId and body are required");
        }

        // 포스트 존재 확인
        Post post = postRepository.findById(req.getPostId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "POST_NOT_FOUND"));

        PostComment comment = PostComment.builder()
                .postId(req.getPostId())
                .parentCommentId(req.getParentCommentId())
                .authorId(req.getAuthorId() != null ? req.getAuthorId() : "admin")
                .body(req.getBody())
                .status(CommentStatus.ACTIVE)
                .createdByAdmin(true)
                .build();

        PostComment saved = postCommentRepository.save(comment);

        Set<String> syntheticIds = userRepository.findSyntheticIds(java.util.List.of(saved.getAuthorId()));
        boolean synthetic = syntheticIds.contains(saved.getAuthorId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AdminCommentView(saved, synthetic));
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
        /** 어드민이 생성한 콘텐츠 여부. ADMIN 전용. */
        private final boolean createdByAdmin;
        /** 미삭제 댓글·대댓글 수. ADMIN 목록용. */
        private final long commentCount;

        public AdminPostView(Post post, boolean synthetic) {
            this(post, synthetic, 0L);
        }

        public AdminPostView(Post post, boolean synthetic, long commentCount) {
            this.post = post;
            this.synthetic = synthetic;
            this.createdByAdmin = post.getCreatedByAdmin() != null && post.getCreatedByAdmin();
            this.commentCount = commentCount;
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
        /** 어드민이 생성한 콘텐츠 여부. ADMIN 전용. */
        private final boolean createdByAdmin;

        public AdminCommentView(PostComment comment, boolean synthetic) {
            this.comment = comment;
            this.synthetic = synthetic;
            this.createdByAdmin = comment.getCreatedByAdmin() != null && comment.getCreatedByAdmin();
        }
    }

    // ===== Response DTOs =====

    /** 원본 비교 화면 응답 */
    @Getter
    public static class SourceComparisonResponse {
        private final boolean synthetic;
        private final boolean hasSource;
        private final SourceData source;
        private final GeneratedData generated;

        public SourceComparisonResponse(boolean synthetic, boolean hasSource, SourceData source, GeneratedData generated) {
            this.synthetic = synthetic;
            this.hasSource = hasSource;
            this.source = source;
            this.generated = generated;
        }

        @Getter
        public static class SourceData {
            private final Long exampleId;
            private final String community;
            private final String url;
            private final String title;
            private final String body;

            public SourceData(Long exampleId, String community, String url, String title, String body) {
                this.exampleId = exampleId;
                this.community = community;
                this.url = url;
                this.title = title;
                this.body = body;
            }
        }

        @Getter
        public static class GeneratedData {
            private final String title;
            private final String body;

            public GeneratedData(String title, String body) {
                this.title = title;
                this.body = body;
            }
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
        private Integer viewCount;
    }

    @Getter
    @Setter
    public static class UpdateCommentRequest {
        private String body;
    }

    // ===== 좋아요 조정 DTO =====

    @Getter
    @Setter
    public static class AdjustLikesRequest {
        /** 좋아요 조정값 (+1 또는 -1) */
        private Integer delta;
    }

    @Getter
    public static class AdjustLikesResponse {
        private final long likeCount;

        public AdjustLikesResponse(long likeCount) {
            this.likeCount = likeCount;
        }
    }

    // ===== 콘텐츠 생성 DTO =====

    @Getter
    @Setter
    public static class CreatePostRequest {
        private String title;
        private String bodyRaw;
        private String category;
        private String authorId; // 선택사항, null이면 "admin"
    }

    @Getter
    @Setter
    public static class CreateCommentRequest {
        private String postId;
        private String body;
        private Long parentCommentId; // 선택사항, null이면 최상위 댓글
        private String authorId; // 선택사항, null이면 "admin"
    }
}
