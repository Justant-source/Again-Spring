package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.domain.ai.BotRequestDedup;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.repository.UserRepository;
import com.againspring.repository.ai.BotRequestDedupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BotWriteIdempotencyServiceTest {

    @Mock private BotRequestDedupRepository dedupRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private BotWriteIdempotencyService service;

    @Test
    void syntheticUserWithKey_isEligible_butRegularUserIsNot() {
        when(userRepository.findById("bot-1")).thenReturn(Optional.of(User.builder().synthetic(true).build()));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(User.builder().synthetic(false).build()));

        assertThat(service.appliesTo("bot-1", "plan-item:abc")).isTrue();
        assertThat(service.appliesTo("user-1", "plan-item:abc")).isFalse();
        assertThat(service.appliesTo("bot-1", null)).isFalse();
    }

    @Test
    void firstClaim_createsOnceAndPersistsPostTargetId() {
        String key = "plan-item:post-1";
        BotRequestDedup mapping = BotRequestDedup.builder().idempotencyKey(key).build();
        when(dedupRepository.claim(key, "POST", "bot-1")).thenReturn(1);
        when(dedupRepository.findById(key)).thenReturn(Optional.of(mapping));

        BotWriteIdempotencyService.Execution<Post> result = service.execute(
                "bot-1", key, BotWriteIdempotencyService.TargetType.POST,
                () -> Post.builder().id("post_created").build(), ignored -> null);

        assertThat(result.created()).isTrue();
        assertThat(result.target().getId()).isEqualTo("post_created");
        assertThat(mapping.getTargetId()).isEqualTo("post_created");
    }

    @Test
    void duplicateKey_returnsExistingCommentWithoutCallingCreate() {
        String key = "plan-item:comment-1";
        BotRequestDedup mapping = BotRequestDedup.builder()
                .idempotencyKey(key).targetType("COMMENT").targetId("42").botUserId("bot-1").build();
        AtomicBoolean created = new AtomicBoolean(false);
        when(dedupRepository.claim(key, "COMMENT", "bot-1")).thenReturn(0);
        when(dedupRepository.findById(key)).thenReturn(Optional.of(mapping));

        BotWriteIdempotencyService.Execution<PostComment> result = service.execute(
                "bot-1", key, BotWriteIdempotencyService.TargetType.COMMENT,
                () -> { created.set(true); return PostComment.builder().id(999L).build(); },
                id -> PostComment.builder().id(Long.valueOf(id)).build());

        assertThat(result.created()).isFalse();
        assertThat(result.target().getId()).isEqualTo(42L);
        assertThat(created).isFalse();
    }

    @Test
    void duplicateKeyForOtherBotOrTargetType_isRejected() {
        String key = "plan-item:conflict";
        BotRequestDedup mapping = BotRequestDedup.builder()
                .idempotencyKey(key).targetType("POST").targetId("post_1").botUserId("bot-other").build();
        when(dedupRepository.claim(key, "COMMENT", "bot-1")).thenReturn(0);
        when(dedupRepository.findById(key)).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> service.execute("bot-1", key, BotWriteIdempotencyService.TargetType.COMMENT,
                () -> PostComment.builder().id(1L).build(), ignored -> null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void invalidKey_isRejectedBeforeClaim() {
        assertThatThrownBy(() -> service.execute("bot-1", "contains space", BotWriteIdempotencyService.TargetType.POST,
                () -> Post.builder().id("post").build(), ignored -> null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_IDEMPOTENCY_KEY");
        verify(dedupRepository, never()).claim(anyString(), anyString(), anyString());
    }
}
