package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostLikeRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.safety.CrisisKeywordGuard;
import com.againspring.safety.CrisisScanResult;
import com.againspring.service.ai.AiUserOutboxWriter;
import com.againspring.service.ai.SyntheticOutputGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CommentService 공개 피드 필터 회귀 테스트.
 * 2026-06-07: 공개 댓글 목록이 deleted_at/status를 필터링하지 않아 차단·삭제 댓글이 노출되던 버그 수정.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService — 공개 피드 차단/삭제 필터")
class CommentServiceTest {

    @Mock private PostCommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private CrisisKeywordGuard crisisKeywordGuard;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AiUserOutboxWriter aiUserOutboxWriter;
    @Mock private SyntheticOutputGuard syntheticOutputGuard;

    @InjectMocks private CommentService commentService;

    private static final String POST_ID = "post_abc";

    @BeforeEach
    void setUp() {
        // 실사용자 위기 관제는 기본적으로 무위기 — 개별 테스트가 필요 시 더 구체적으로 덮어쓴다.
        lenient().when(crisisKeywordGuard.scan(anyString())).thenReturn(CrisisScanResult.none());
    }

    @Test
    @DisplayName("getTopLevelComments — ACTIVE & deletedAt IS NULL 필터 쿼리만 사용 (무필터 쿼리 미사용)")
    void getTopLevelComments_usesActiveFilteredQuery() {
        PostComment visible = PostComment.builder()
                .id(1L).postId(POST_ID).body("보임").status(CommentStatus.ACTIVE).build();
        when(commentRepository
                .findByPostIdAndParentCommentIdIsNullAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(POST_ID, CommentStatus.ACTIVE))
                .thenReturn(List.of(visible));

        List<PostComment> result = commentService.getTopLevelComments(POST_ID);

        assertThat(result).containsExactly(visible);
        verify(commentRepository)
                .findByPostIdAndParentCommentIdIsNullAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(POST_ID, CommentStatus.ACTIVE);
        // 무필터(레거시) 쿼리는 더 이상 호출되면 안 됨
        verify(commentRepository, never()).findByPostIdAndParentCommentIdIsNullOrderByCreatedAtAsc(anyString());
    }

    @Test
    @DisplayName("getReplies — ACTIVE & deletedAt IS NULL 필터 쿼리만 사용 (무필터 쿼리 미사용)")
    void getReplies_usesActiveFilteredQuery() {
        Long parentId = 10L;
        PostComment reply = PostComment.builder()
                .id(11L).postId(POST_ID).parentCommentId(parentId).body("답글").status(CommentStatus.ACTIVE).build();
        when(commentRepository
                .findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(parentId, CommentStatus.ACTIVE))
                .thenReturn(List.of(reply));

        List<PostComment> result = commentService.getReplies(parentId);

        assertThat(result).containsExactly(reply);
        verify(commentRepository)
                .findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(parentId, CommentStatus.ACTIVE);
        verify(commentRepository, never()).findByParentCommentIdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    @DisplayName("addComment — 대댓글의 대댓글(depth≥2)은 COMMENT_DEPTH_EXCEEDED")
    void addComment_rejectsReplyToReply() {
        Long depth1Id = 10L;
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(Post.builder().id(POST_ID).build()));
        when(commentRepository.findById(depth1Id)).thenReturn(Optional.of(PostComment.builder()
                .id(depth1Id).postId(POST_ID).parentCommentId(1L).body("직계 대댓글").status(CommentStatus.ACTIVE).build()));

        assertThatThrownBy(() -> commentService.addComment(POST_ID, depth1Id, "user-1", "중첩 답글"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("COMMENT_DEPTH_EXCEEDED");
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addComment — synthetic 작성자의 LLM 오류 출력 문자열은 SyntheticOutputGuard가 거부한다")
    void addComment_rejectsSyntheticAuthorErrorOutput() {
        String body = "Your credit balance is too low to access the Anthropic API.";
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(Post.builder().id(POST_ID).build()));
        doThrow(new BusinessException(SyntheticOutputGuard.CODE, "AI 출력 오류 문자열은 게시할 수 없습니다", 422))
                .when(syntheticOutputGuard).assertPublishable(eq("ai_persona_1"), eq(body));

        assertThatThrownBy(() -> commentService.addComment(POST_ID, null, "ai_persona_1", body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("게시할 수 없습니다");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("addComment — 실사용자가 동일한 오류 문자열을 쓰더라도 SyntheticOutputGuard는 막지 않는다(fail-open)")
    void addComment_realUserSameErrorStringIsPublished() {
        String body = "Your credit balance is too low to access the Anthropic API.";
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(Post.builder().id(POST_ID).authorId("other-user").build()));
        when(syntheticOutputGuard.isSynthetic("real_user_1")).thenReturn(false);
        when(crisisKeywordGuard.scan(eq(body))).thenReturn(CrisisScanResult.none());
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // syntheticOutputGuard는 실사용자에 대해 stub 없이도(fail-open) 아무 것도 던지지 않는다.

        assertThatCode(() -> commentService.addComment(POST_ID, null, "real_user_1", body))
                .doesNotThrowAnyException();

        verify(commentRepository).save(any());
    }

    @Test
    @DisplayName("deleteComment(최상위) — 대댓글 정리는 무필터 쿼리로 차단·삭제 답글까지 전부 삭제 (orphan 방지)")
    void deleteComment_topLevel_cascadesUsingUnfilteredQuery() {
        Long commentId = 100L;
        String userId = "user-1";
        PostComment top = PostComment.builder()
                .id(commentId).postId(POST_ID).authorId(userId).body("최상위").status(CommentStatus.ACTIVE).build();
        PostComment blockedReply = PostComment.builder()
                .id(101L).postId(POST_ID).parentCommentId(commentId).authorId(userId).body("차단된 답글").status(CommentStatus.BLOCKED).build();

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(top));
        when(commentRepository.findByParentCommentIdOrderByCreatedAtAsc(commentId)).thenReturn(List.of(blockedReply));
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(Post.builder().id(POST_ID).build()));

        commentService.deleteComment(commentId, userId);

        // cascade는 무필터 쿼리로 (차단·삭제된 답글도 함께 제거되어야 함)
        verify(commentRepository).findByParentCommentIdOrderByCreatedAtAsc(commentId);
        verify(commentRepository, never())
                .findByParentCommentIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(anyLong(), any());
        verify(commentRepository).delete(blockedReply);
        verify(commentRepository).delete(top);
        verify(postLikeRepository).deleteByCommentId(101L);
        verify(postLikeRepository).deleteByCommentId(commentId);
    }
}
