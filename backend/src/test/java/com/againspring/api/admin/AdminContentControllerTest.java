package com.againspring.api.admin;

import com.againspring.domain.community.Post;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostCommentRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiCorrectionService;
import com.againspring.service.ai.AiUserOutboxWriter;
import com.againspring.service.admin.AdminPublishedThreadService;
import com.againspring.service.community.PostSearchNgramIndexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminContentControllerTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostCommentRepository postCommentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiCorrectionService aiCorrectionService;

    @Mock
    private AiUserOutboxWriter aiUserOutboxWriter;

    @Mock
    private AdminPublishedThreadService publishedThreadService;

    @Mock
    private PostSearchNgramIndexer postSearchNgramIndexer;

    @InjectMocks
    private AdminContentController controller;

    @Test
    void updatePost_syncsUserTitleWhenAdminUpdatesTitle() {
        Post post = Post.builder()
            .id("post-1")
            .title("before title")
            .userTitle("before user title")
            .bodyRaw("body")
            .bodyPublished("body")
            .build();
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(post)).thenReturn(post);

        AdminContentController.UpdatePostRequest request = new AdminContentController.UpdatePostRequest();
        request.setTitle("after title");

        ResponseEntity<Post> response = controller.updatePost("post-1", request, null);

        ArgumentCaptor<Post> savedCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(savedCaptor.capture());

        Post saved = savedCaptor.getValue();
        assertThat(response.getBody()).isSameAs(post);
        assertThat(saved.getTitle()).isEqualTo("after title");
        assertThat(saved.getUserTitle()).isEqualTo("after title");
    }
}
