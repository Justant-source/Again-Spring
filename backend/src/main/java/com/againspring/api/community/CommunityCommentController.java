package com.againspring.api.community;

import com.againspring.api.community.dto.*;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.CommunityReport;
import com.againspring.domain.community.Post;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.community.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    /**
     * 댓글 목록 조회
     * GET /api/community/posts/{postId}/comments
     */
    @GetMapping
    @Operation(summary = "댓글 목록 조회", description = "최상위 댓글과 각 대댓글 포함")
    public ResponseEntity<List<CommentWithRepliesResponse>> getComments(
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails != null ? userDetails.getUsername() : null;

        // 포스트 정보 조회 (authorId, partnerUserId 확인)
        Post post = postRepository.findById(postId).orElse(null);
        String postAuthorId = post != null ? post.getAuthorId() : null;
        String postPartnerUserId = post != null ? post.getPartnerUserId() : null;

        List<PostComment> topLevelComments = commentService.getTopLevelComments(postId);

        List<CommentWithRepliesResponse> responses = topLevelComments.stream()
                .map(comment -> {
                    List<PostComment> replies = commentService.getReplies(comment.getId());

                    // 댓글 작성자가 포스트 작성자인지, 파트너인지 확인
                    boolean isAuthor = postAuthorId != null && postAuthorId.equals(comment.getAuthorId());
                    boolean isPartner = postPartnerUserId != null && postPartnerUserId.equals(comment.getAuthorId());

                    List<CommentResponse> replyResponses = replies.stream()
                            .map(reply -> {
                                boolean replyIsAuthor = postAuthorId != null && postAuthorId.equals(reply.getAuthorId());
                                boolean replyIsPartner = postPartnerUserId != null && postPartnerUserId.equals(reply.getAuthorId());
                                // TODO: isLiked 조회
                                return CommentResponse.from(reply, false, replyIsAuthor, replyIsPartner);
                            })
                            .toList();

                    // TODO: isLiked 조회
                    return CommentWithRepliesResponse.builder()
                            .id(comment.getId())
                            .authorId(comment.getAuthorId())
                            .body(comment.getBody())
                            .likeCount((long) comment.getLikeCount())
                            .isLiked(false)
                            .createdAt(comment.getCreatedAt())
                            .isAuthor(isAuthor)
                            .isPartner(isPartner)
                            .replies(replyResponses)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(responses);
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
            @AuthenticationPrincipal UserDetails userDetails) {

        PostComment comment = commentService.addComment(
                postId,
                request.getParentCommentId(),
                userDetails.getUsername(),
                request.getBody()
        );

        CommentResponse response = CommentResponse.from(comment, false);
        return ResponseEntity.ok(response);
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
            @AuthenticationPrincipal UserDetails userDetails) {

        boolean liked = commentService.toggleCommentLike(commentId, userDetails.getUsername());
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
            @AuthenticationPrincipal UserDetails userDetails) {

        CommunityReport report = CommunityReport.builder()
                .targetType("COMMENT")
                .targetId(String.valueOf(commentId))
                .reporterUserId(userDetails.getUsername())
                .reason(request.getReason())
                .status("PENDING")
                .build();

        communityReportRepository.save(report);
        log.info("Comment reported: {} by user {}, reason: {}", commentId, userDetails.getUsername(), request.getReason());

        return ResponseEntity.ok(com.againspring.api.community.dto.ReportResponse.builder()
                .reported(true)
                .build());
    }
}
