package com.againspring.service.admin;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiCorrectionService;
import com.againspring.service.ai.AiUserOutboxWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPublishedThreadServiceTest {

    @Mock PostRepository postRepository;
    @Mock PostCommentRepository postCommentRepository;
    @Mock UserRepository userRepository;
    @Mock AiCorrectionService aiCorrectionService;
    @Mock AiUserOutboxWriter aiUserOutboxWriter;
    @InjectMocks AdminPublishedThreadService service;

    @Test
    void getThreadReturnsPostAndItemsOrdered() {
        Post post = Post.builder()
                .id("p1")
                .title("제목")
                .bodyRaw("본문")
                .bodyPublished("본문")
                .category(PostCategory.COUPLE)
                .status(PostStatus.VOTING)
                .authorId("author-a")
                .createdAt(Instant.parse("2026-08-01T01:00:00Z"))
                .build();
        PostComment comment = PostComment.builder()
                .id(10L)
                .postId("p1")
                .authorId("c1")
                .body("댓글")
                .status(CommentStatus.ACTIVE)
                .createdAt(Instant.parse("2026-08-01T02:00:00Z"))
                .build();
        PostComment reply = PostComment.builder()
                .id(11L)
                .postId("p1")
                .parentCommentId(10L)
                .authorId("c2")
                .body("대댓글")
                .status(CommentStatus.ACTIVE)
                .createdAt(Instant.parse("2026-08-01T03:00:00Z"))
                .build();

        when(postRepository.findById("p1")).thenReturn(Optional.of(post));
        when(postCommentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc("p1"))
                .thenReturn(List.of(comment, reply));
        when(userRepository.findSyntheticIds(any())).thenReturn(Set.of("c1"));

        Map<String, Object> view = service.getThread("p1");

        assertThat(view.get("title")).isEqualTo("제목");
        assertThat(view.get("commentCount")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) view.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("type")).isEqualTo("COMMENT");
        assertThat(items.get(1).get("type")).isEqualTo("REPLY");
        assertThat(items.get(0).get("synthetic")).isEqualTo(true);
    }

    @Test
    void patchThreadUpdatesCreatedAtAndSoftDeletesMissingItems() {
        Post post = Post.builder()
                .id("p1")
                .title("제목")
                .bodyRaw("본문")
                .bodyPublished("본문")
                .category(PostCategory.OTHER)
                .status(PostStatus.VOTING)
                .authorId("a")
                .createdAt(Instant.parse("2026-08-01T01:00:00Z"))
                .build();
        PostComment keep = PostComment.builder()
                .id(1L).postId("p1").authorId("c1").body("keep")
                .status(CommentStatus.ACTIVE)
                .createdAt(Instant.parse("2026-08-01T02:00:00Z")).build();
        PostComment drop = PostComment.builder()
                .id(2L).postId("p1").authorId("c2").body("drop")
                .status(CommentStatus.ACTIVE)
                .createdAt(Instant.parse("2026-08-01T02:30:00Z")).build();

        when(postRepository.findById("p1")).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postCommentRepository.findByPostIdAndDeletedAtIsNullOrderByCreatedAtAsc("p1"))
                .thenReturn(List.of(keep, drop))
                .thenReturn(List.of(keep));
        when(postCommentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findSyntheticIds(any())).thenReturn(Set.of());

        AdminPublishedThreadService.UpdateThreadRequest req = new AdminPublishedThreadService.UpdateThreadRequest();
        req.setCreatedAt("2026-08-01T04:00:00Z");
        AdminPublishedThreadService.ThreadItemRequest item = new AdminPublishedThreadService.ThreadItemRequest();
        item.setId(1L);
        item.setBody("keep-edited");
        item.setCreatedAt("2026-08-01T05:00:00Z");
        req.setItems(List.of(item));

        Map<String, Object> view = service.patchThread("p1", req, "admin");

        assertThat(post.getCreatedAt()).isEqualTo(Instant.parse("2026-08-01T04:00:00Z"));
        assertThat(keep.getBody()).isEqualTo("keep-edited");
        assertThat(keep.getCreatedAt()).isEqualTo(Instant.parse("2026-08-01T05:00:00Z"));
        assertThat(drop.getDeletedAt()).isNotNull();
        verify(aiUserOutboxWriter).commentLifecycleChanged(eq(post), eq(drop), eq("COMMENT_DELETED"), eq("ADMIN_THREAD_EDIT"));
        assertThat(view.get("commentCount")).isEqualTo(1);
    }
}
