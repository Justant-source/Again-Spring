package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.common.exception.BusinessException;
import com.againspring.safety.CrisisDetectedEvent;
import com.againspring.safety.CrisisKeywordGuard;
import com.againspring.safety.CrisisScanResult;
import com.againspring.service.ai.AiUserOutboxWriter;
import com.againspring.service.ai.SyntheticOutputGuard;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 광장형 정책: 사연 본문의 위기 키워드(피해자·소송 등)는 게시 차단 사유가 아니다.
 * 2026-08-10: PostComposeService가 (구)KeywordGuard isBlocked()로 예약 발행을 막던 회귀.
 * 현재는 CrisisKeywordGuard.scan()이 CrisisDetectedEvent만 남기고 게시는 항상 진행한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostComposeService — 광장형 입력 미차단")
class PostComposeServicePlazaPolicyTest {

    @Mock private PostRepository postRepository;
    @Mock private VoteOptionRepository voteOptionRepository;
    @Mock private CrisisKeywordGuard crisisKeywordGuard;
    @Mock private AiUserOutboxWriter aiUserOutboxWriter;
    @Mock private PostSearchNgramIndexer postSearchNgramIndexer;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private SibomCandidateService sibomCandidateService;
    @Mock private SyntheticOutputGuard syntheticOutputGuard;

    @InjectMocks private PostComposeService composeService;

    @Test
    @DisplayName("위기 키워드(피해자·소송) 본문도 게시된다 — CRISIS_DETECTED로 막지 않는다")
    void compose_doesNotBlockAnyWording() {
        String body = "결국 경찰 신고와 민사 소송, 그리고 공론화를 결심했습니다. "
                + "억울하게 피해자가 먼저 떠나야 하는 상황은 절대 만들고 싶지 않습니다.";
        when(syntheticOutputGuard.isSynthetic("user_bot")).thenReturn(false);
        when(crisisKeywordGuard.scan(body)).thenReturn(CrisisScanResult.none());
        when(sibomCandidateService.shortlist(anyString(), any())).thenReturn(List.of());
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
    }

    @Test
    @DisplayName("위기 키워드 감지 시에도 게시는 계속되고 CrisisDetectedEvent가 감사 로그용으로 발행된다")
    void compose_crisisDetected_stillPublishesAndEmitsAuditEvent() {
        String body = "이제 정말 죽고싶다는 생각뿐입니다.";
        when(syntheticOutputGuard.isSynthetic("user_real")).thenReturn(false);
        when(crisisKeywordGuard.scan(body)).thenReturn(new CrisisScanResult(true, List.of("죽고싶")));
        when(sibomCandidateService.shortlist(anyString(), any())).thenReturn(List.of());
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> composeService.composeAndPublish(
                "user_real", "제목", body,
                PostCategory.FAMILY, "PUBLIC", "session-99", null, null, null, null, null))
                .doesNotThrowAnyException();

        // (a) 위기 감지와 무관하게 게시는 계속된다
        verify(postRepository).save(any(Post.class));

        // (b) CrisisDetectedEvent가 정확히 1회, 매칭된 패턴과 함께 발행된다
        ArgumentCaptor<CrisisDetectedEvent> eventCaptor = ArgumentCaptor.forClass(CrisisDetectedEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(eventCaptor.capture());
        CrisisDetectedEvent published = eventCaptor.getValue();
        assertThat(published.getUserId()).isEqualTo("user_real");
        assertThat(published.getSessionId()).isEqualTo("session-99");
        assertThat(published.getMatchedPatterns()).containsExactly("죽고싶");
    }

    @Test
    @DisplayName("위기 키워드 미감지 시 CrisisDetectedEvent는 발행되지 않는다")
    void compose_noCrisis_neverPublishesCrisisDetectedEvent() {
        String body = "평범한 일상 이야기입니다.";
        when(syntheticOutputGuard.isSynthetic("user_real")).thenReturn(false);
        when(crisisKeywordGuard.scan(body)).thenReturn(CrisisScanResult.none());
        when(sibomCandidateService.shortlist(anyString(), any())).thenReturn(List.of());
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        composeService.composeAndPublish(
                "user_real", "제목", body,
                PostCategory.FAMILY, "PUBLIC", null, null, null, null, null, null);

        verify(postRepository).save(any(Post.class));
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(CrisisDetectedEvent.class));
    }

    @Test
    @DisplayName("synthetic 작성자의 LLM 오류 출력 문자열은 SyntheticOutputGuard가 거부한다")
    void compose_rejectsSyntheticAuthorErrorOutput() {
        String body = "Your credit balance is too low to access the Anthropic API.";
        doThrow(new BusinessException(SyntheticOutputGuard.CODE, "AI 출력 오류 문자열은 게시할 수 없습니다", 422))
                .when(syntheticOutputGuard).assertPublishable(eq("ai_persona_1"), eq(body));

        assertThatThrownBy(() -> composeService.composeAndPublish(
                "ai_persona_1", "제목", body,
                PostCategory.FAMILY, "PUBLIC", null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("게시할 수 없습니다");

        verify(postRepository, org.mockito.Mockito.never()).save(any(Post.class));
    }

    @Test
    @DisplayName("실사용자가 동일한 오류 문자열을 쓰더라도 SyntheticOutputGuard는 막지 않는다(fail-open)")
    void compose_realUserSameErrorStringIsPublished() {
        String body = "Your credit balance is too low to access the Anthropic API.";
        when(syntheticOutputGuard.isSynthetic("real_user_1")).thenReturn(false);
        when(crisisKeywordGuard.scan(body)).thenReturn(CrisisScanResult.none());
        when(sibomCandidateService.shortlist(anyString(), any())).thenReturn(List.of());
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voteOptionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        // syntheticOutputGuard는 실사용자에 대해 stub 없이도(fail-open) 아무 것도 던지지 않는다.

        assertThatCode(() -> composeService.composeAndPublish(
                "real_user_1", "제목", body,
                PostCategory.FAMILY, "PUBLIC", null, null, null, null, null, null))
                .doesNotThrowAnyException();

        verify(postRepository).save(any(Post.class));
    }
}
