package com.againspring.api.community;

import com.againspring.api.community.dto.*;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.CommunityReport;
import com.againspring.domain.community.Post;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.PostLikeRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.community.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CommunityCommentController - 커뮤니티 댓글 API
 */
@RestController
@RequestMapping("/api/community/posts/{postId}/comments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Community", description = "커뮤니티 댓글 관리")
public class CommunityCommentController {

    private final CommentService commentService;
    private final CommunityReportRepository communityReportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;

    /**
     * 댓글 목록 조회
     * GET /api/community/posts/{postId}/comments
     */
    @GetMapping
    @Operation(summary = "댓글 목록 조회", description = "최상위 댓글과 각 대댓글 포함 (페이지네이션)")
    public ResponseEntity<List<CommentWithRepliesResponse>> getComments(
            @PathVariable String postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails != null ? userDetails.getUsername() : null;

        // 포스트 정보 조회 (authorId, partnerUserId 확인)
        Post post = postRepository.findById(postId).orElse(null);
        String postAuthorId = post != null ? post.getAuthorId() : null;
        String postPartnerUserId = post != null ? post.getPartnerUserId() : null;

        List<PostComment> allTopLevel = commentService.getTopLevelComments(postId);
        // 페이지네이션 (Java-side, 댓글 수가 많지 않으므로 DB 쿼리 추가 없이 처리)
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allTopLevel.size());
        List<PostComment> topLevelComments = fromIndex >= allTopLevel.size()
                ? java.util.Collections.emptyList()
                : allTopLevel.subList(fromIndex, toIndex);

        List<CommentWithRepliesResponse> responses = topLevelComments.stream()
                .map(comment -> {
                    List<PostComment> replies = commentService.getReplies(comment.getId());

                    boolean isAuthor = postAuthorId != null && postAuthorId.equals(comment.getAuthorId());
                    boolean isPartner = postPartnerUserId != null && postPartnerUserId.equals(comment.getAuthorId());
                    String authorNickname = resolveNickname(comment.getAuthorId());
                    boolean isLiked = userId != null && postLikeRepository.existsByCommentIdAndUserId(comment.getId(), userId);
                    boolean isMine = userId != null && userId.equals(comment.getAuthorId());

                    List<CommentResponse> replyResponses = replies.stream()
                            .map(reply -> {
                                boolean replyIsAuthor = postAuthorId != null && postAuthorId.equals(reply.getAuthorId());
                                boolean replyIsPartner = postPartnerUserId != null && postPartnerUserId.equals(reply.getAuthorId());
                                String replyNickname = resolveNickname(reply.getAuthorId());
                                boolean replyIsLiked = userId != null && postLikeRepository.existsByCommentIdAndUserId(reply.getId(), userId);
                                boolean replyIsMine = userId != null && userId.equals(reply.getAuthorId());
                                return CommentResponse.from(reply, replyIsLiked, replyIsAuthor, replyIsPartner, replyNickname, replyIsMine);
                            })
                            .toList();

                    return CommentWithRepliesResponse.builder()
                            .id(comment.getId())
                            .authorId(comment.getAuthorId())
                            .authorNickname(authorNickname)
                            .body(comment.getBody())
                            .likeCount((long) comment.getLikeCount())
                            .isLiked(isLiked)
                            .createdAt(comment.getCreatedAt())
                            .isAuthor(isAuthor)
                            .isPartner(isPartner)
                            .isMine(isMine)
                            .replies(replyResponses)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    /** Authentication → userId (null이면 미인증) */
    private String resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        String name = authentication.getName();
        return "anonymousUser".equals(name) ? null : name;
    }

    /** 사용자 ID → 닉네임 변환 (없으면 익명 반환) */
    private String resolveNickname(String userId) {
        if (userId == null || userId.startsWith("anon_")) return "익명";
        return userRepository.findById(userId)
                .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
                .orElse("익명");
    }

    /**
     * 댓글 작성
     * POST /api/community/posts/{postId}/comments
     */
    @PostMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "댓글 작성", description = "새 댓글 또는 대댓글 작성")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable String postId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        // 미인증 사용자도 익명으로 댓글 가능
        if (userId == null) {
            userId = "anon_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }

        PostComment comment = commentService.addComment(
                postId,
                request.getParentCommentId(),
                userId,
                request.getBody()
        );

        CommentResponse response = CommentResponse.from(comment, false);
        return ResponseEntity.ok(response);
    }

    /**
     * 댓글 수정 (본인만)
     * PUT /api/community/posts/{postId}/comments/{commentId}
     */
    @PutMapping("/{commentId}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "댓글 수정", description = "본인이 작성한 댓글 내용 수정")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable String postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        if (userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        PostComment updated = commentService.updateComment(commentId, userId, request.getBody());
        boolean isLiked = postLikeRepository.existsByCommentIdAndUserId(commentId, userId);
        String nickname = resolveNickname(updated.getAuthorId());
        return ResponseEntity.ok(
                CommentResponse.from(updated, isLiked, false, false, nickname, true));
    }

    /**
     * 댓글 삭제 (본인만)
     * DELETE /api/community/posts/{postId}/comments/{commentId}
     */
    @DeleteMapping("/{commentId}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "댓글 삭제", description = "본인이 작성한 댓글 삭제 (대댓글 포함)")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String postId,
            @PathVariable Long commentId,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        if (userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        commentService.deleteComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 댓글 좋아요 토글
     * POST /api/community/posts/{postId}/comments/{commentId}/like
     */
    @PostMapping("/{commentId}/like")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "댓글 좋아요 토글", description = "댓글에 좋아요를 추가하거나 제거")
    public ResponseEntity<LikeResponse> toggleCommentLike(
            @PathVariable String postId,
            @PathVariable Long commentId,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        if (userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        boolean liked = commentService.toggleCommentLike(commentId, userId);
        long count = commentService.getCommentLikeCount(commentId);

        LikeResponse response = LikeResponse.builder()
                .liked(liked)
                .count(count)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 댓글 신고
     * POST /api/community/posts/{postId}/comments/{commentId}/report
     */
    @PostMapping("/{commentId}/report")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "댓글 신고", description = "부적절한 댓글을 신고")
    public ResponseEntity<com.againspring.api.community.dto.ReportResponse> reportComment(
            @PathVariable String postId,
            @PathVariable Long commentId,
            @Valid @RequestBody ReportRequest request,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        if (userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        CommunityReport report = CommunityReport.builder()
                .targetType("COMMENT")
                .targetId(String.valueOf(commentId))
                .reporterUserId(userId)
                .reason(request.getReason())
                .status("PENDING")
                .build();

        communityReportRepository.save(report);
        log.info("Comment reported: {} by user {}, reason: {}", commentId, userId, request.getReason());

        return ResponseEntity.ok(com.againspring.api.community.dto.ReportResponse.builder()
                .reported(true)
                .build());
    }
}
