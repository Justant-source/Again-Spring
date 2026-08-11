package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.Level;
import com.againspring.safety.ScanResult;
import com.againspring.service.ai.AiUserOutboxWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 광장형 정책: 사연 본문의 금지어/LEVEL1(피해자·소송 등)은 게시 차단 사유가 아니다.
 * 2026-08-10: PostComposeService가 KeywordGuard isBlocked()로 예약 발행을 막던 회귀.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostComposeService — 광장형 입력 미차단")
class PostComposeServicePlazaPolicyTest {

    @Mock private PostRepository postRepository;
    @Mock private VoteOptionRepository voteOptionRepository;
    @Mock private KeywordGuard keywordGuard;
    @Mock private AiUserOutboxWriter aiUserOutboxWriter;
    @Mock private PostSearchNgramIndexer postSearchNgramIndexer;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PostComposeService composeService;

    @Test
    @DisplayName("LEVEL1(피해자·소송) 본문도 게시된다 — CRISIS_DETECTED로 막지 않는다")
    void compose_doesNotBlockLevel1LegalWords() {
        String body = "결국 경찰 신고와 민사 소송, 그리고 공론화를 결심했습니다. "
                + "억울하게 피해자가 먼저 떠나야 하는 상황은 절대 만들고 싶지 않습니다.";
        when(keywordGuard.scanUserInput(eq(body), anyString())).thenReturn(
                ScanResult.blockedResult(Level.LEVEL1, List.of(
                        new ScanResult.Match("소송", Level.LEVEL1, "LEGAL_RISK", false, 10),
                        new ScanResult.Match("피해자", Level.LEVEL1, "STIGMA", false, 40)
                )));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> composeService.composeAndPublish(
                "user_bot", "딸이 꺼낸 말 한마디에 세상이 무너졌습니다", body,
                PostCategory.FAMILY, "PUBLIC", null, null, null, null, null, null))
                .doesNotThrowAnyException();

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getBodyRaw()).contains("소송");
        assertThat(captor.getValue().getBodyRaw()).contains("피해자");
        verify(keywordGuard).scanUserInput(eq(body), eq("user_bot"));
    }
}
