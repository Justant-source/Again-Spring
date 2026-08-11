package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.enums.PublishMode;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiUserOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostService delete / tombstone")
class PostServiceDeleteTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private AiUserOutboxWriter aiUserOutboxWriter;

    @Mock
    private PostSearchNgramIndexer postSearchNgramIndexer;

    @Mock
    private CommentService commentService;

    @InjectMocks
    private PostService postService;

    private Post post;
    private final String POST_ID = "post-del-001";
    private final String AUTHOR_ID = "author-001";

    @BeforeEach
    void setUp() {
        post = Post.builder()
                .id(POST_ID)
                .authorId(AUTHOR_ID)
                .title("제목 유지")
                .status(PostStatus.VOTING)
                .visibility(PostVisibility.PUBLIC)
                .publishMode(PublishMode.PUBLISH_NOW)
                .bodyRaw("작성자 본문")
                .bodyPublished("작성자 본문")
                .contentRevision(1)
                .build();
    }

    @Test
    @DisplayName("상대 ACTIVE → 작성자 본문만 tombstone, 제목·상대 유지")
    void delete_whenPartnerActive_tombstonesAuthorOnly() {
        post.setPartnerBodyRaw("상대 본문");
        post.setPartnerBodyPublished("상대 본문");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.deletePost(POST_ID, AUTHOR_ID);

        assertNull(result.getBodyRaw());
        assertNull(result.getBodyPublished());
        assertNotNull(result.getAuthorBodyDeletedAt());
        assertNull(result.getDeletedAt());
        assertEquals("제목 유지", result.getTitle());
        assertEquals("상대 본문", result.getPartnerBodyPublished());
        verify(commentService, never()).hardDeleteAllForPost(any());
        verify(aiUserOutboxWriter).postRevised(any(Post.class), eq("AUTHOR_BODY_TOMBSTONED"));
        verify(postSearchNgramIndexer).reindex(any(Post.class));
    }

    @Test
    @DisplayName("상대 NONE → soft full-delete + 댓글 hard delete")
    void delete_whenPartnerNone_fullDeletes() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.deletePost(POST_ID, AUTHOR_ID);

        assertNotNull(result.getDeletedAt());
        verify(commentService).hardDeleteAllForPost(POST_ID);
        verify(aiUserOutboxWriter).postLifecycleChanged(any(Post.class), eq("POST_DELETED"), eq("AUTHOR_DELETED"));
        verify(postRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("상대 TOMBSTONE → soft full-delete")
    void delete_whenPartnerTombstone_fullDeletes() {
        post.setPartnerBodyDeletedAt(Instant.now());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.deletePost(POST_ID, AUTHOR_ID);

        assertNotNull(result.getDeletedAt());
        verify(commentService).hardDeleteAllForPost(POST_ID);
        verify(aiUserOutboxWriter).postLifecycleChanged(any(Post.class), eq("POST_DELETED"), eq("AUTHOR_DELETED"));
    }

    @Test
    @DisplayName("양쪽 tombstone 완결(상대 먼저) → full delete")
    void tombstonePartner_whenAuthorAlreadyTombstone_fullDeletes() {
        post.setAuthorBodyDeletedAt(Instant.now());
        post.setBodyRaw(null);
        post.setBodyPublished(null);
        post.setPartnerBodyRaw("상대");
        post.setPartnerBodyPublished("상대");
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.tombstonePartnerBody(post);

        assertNotNull(result.getDeletedAt());
        assertNotNull(result.getPartnerBodyDeletedAt());
        verify(commentService).hardDeleteAllForPost(POST_ID);
        verify(aiUserOutboxWriter).postLifecycleChanged(any(Post.class), eq("POST_DELETED"), eq("BOTH_SIDES_TOMBSTONED"));
    }

    @Test
    @DisplayName("양쪽 tombstone 완결(작성자 먼저) → full delete")
    void tombstoneAuthor_whenPartnerAlreadyTombstone_fullDeletes() {
        post.setPartnerBodyDeletedAt(Instant.now());
        post.setPartnerBodyRaw(null);
        post.setPartnerBodyPublished(null);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.tombstoneAuthorBody(post);

        assertNotNull(result.getDeletedAt());
        verify(commentService).hardDeleteAllForPost(POST_ID);
    }

    @Test
    @DisplayName("비작성자 삭제 → 403")
    void delete_nonAuthor_forbidden() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postService.deletePost(POST_ID, "other-user"));
        assertEquals("ACCESS_DENIED", ex.getCode());
    }

    @Test
    @DisplayName("getPost — deletedAt 있으면 엔티티 반환 (404 아님)")
    void getPost_softDeleted_returnsEntity() {
        post.setDeletedAt(Instant.now());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        Post result = postService.getPost(POST_ID, null);
        assertNotNull(result.getDeletedAt());
    }

    @Test
    @DisplayName("updateAuthorBody — tombstone 재작성 시 authorBodyDeletedAt 해제")
    void updateAuthorBody_clearsTombstone() {
        post.setAuthorBodyDeletedAt(Instant.now());
        post.setBodyRaw(null);
        post.setBodyPublished(null);
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post result = postService.updateAuthorBody(post, "다시 쓴 본문");

        assertNull(result.getAuthorBodyDeletedAt());
        assertEquals("다시 쓴 본문", result.getBodyPublished());
    }
}
