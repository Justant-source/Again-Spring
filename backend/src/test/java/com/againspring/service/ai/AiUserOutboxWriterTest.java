package com.againspring.service.ai;

import com.againspring.domain.User;
import com.againspring.domain.ai.AiUserOutboxEvent;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.repository.UserRepository;
import com.againspring.repository.ai.AiUserOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiUserOutboxWriterTest {
    @Mock private AiUserOutboxEventRepository outboxRepository;
    @Mock private UserRepository userRepository;

    @Test
    void commentEventCarriesSyntheticAuthorFact() throws Exception {
        when(userRepository.findById("ai-user")).thenReturn(Optional.of(User.builder().id("ai-user").synthetic(true).build()));
        when(outboxRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        AiUserOutboxWriter writer = new AiUserOutboxWriter(outboxRepository, userRepository, new ObjectMapper());

        writer.commentCreated(Post.builder().id("post-1").contentRevision(1).build(),
                PostComment.builder().id(10L).postId("post-1").authorId("ai-user").contentRevision(1).build());

        ArgumentCaptor<AiUserOutboxEvent> event = ArgumentCaptor.forClass(AiUserOutboxEvent.class);
        verify(outboxRepository).save(event.capture());
        assertTrue(new ObjectMapper().readTree(event.getValue().getPayload()).path("syntheticAuthor").asBoolean());
    }
}
