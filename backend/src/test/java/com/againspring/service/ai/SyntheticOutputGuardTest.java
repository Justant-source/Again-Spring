package com.againspring.service.ai;

import com.againspring.domain.User;
import com.againspring.common.exception.BusinessException;
import com.againspring.repository.UserRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyntheticOutputGuardTest {
    private final UserRepository users = mock(UserRepository.class);
    private final SyntheticOutputGuard guard = new SyntheticOutputGuard(users);

    private User user(boolean synthetic) { User u = new User(); u.setId("u1"); u.setSynthetic(synthetic); return u; }

    @Test
    void rejectsErrorStringFromSyntheticAuthor() {
        when(users.findById("u1")).thenReturn(Optional.of(user(true)));
        assertThatThrownBy(() -> guard.assertPublishableIfSynthetic("u1", "Your credit balance is too low to access the Anthropic API."))
                .isInstanceOf(BusinessException.class).hasMessageContaining("게시할 수 없습니다");
    }

    @Test
    void neverTouchesRealUsers() {
        when(users.findById("u1")).thenReturn(Optional.of(user(false)));
        assertThat(guard.assertPublishableIfSynthetic("u1", "Your credit balance is too low")).isFalse();
    }

    @Test
    void allowsAnyKoreanContentIncludingProfanity() {
        when(users.findById("u1")).thenReturn(Optional.of(user(true)));
        assertThat(guard.assertPublishableIfSynthetic("u1", "아 진짜 개빡치네 씨발 저런 놈이랑 왜 사냐 판결이고 뭐고 그냥 손절해"))
                .isTrue();
    }

    @Test
    void findsAuthorExactlyOnceRegardlessOfSyntheticStatus() {
        when(users.findById("u1")).thenReturn(Optional.of(user(true)));
        guard.assertPublishableIfSynthetic("u1", "정상적인 한국어 본문입니다");
        org.mockito.Mockito.verify(users, org.mockito.Mockito.times(1)).findById("u1");
    }
}
