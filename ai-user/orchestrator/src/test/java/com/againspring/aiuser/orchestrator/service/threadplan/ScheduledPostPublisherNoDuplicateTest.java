package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.notification.ScheduledPostTelegramNotifier;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPartnerAnswerRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2026-09-05 리뷰 회귀 — 발행 성공 후 후처리(상대방 예약·phase1 댓글·댓글 재생)가 실패해도
 * 글이 두 번 게시되면 안 된다. 예전에는 후처리 예외가 바깥 catch로 전파돼 row가 재시도
 * 상태로 되돌아갔고, 다음 틱이 {@code createPost}를 다시 불러 같은 사연이 중복 게시됐다.
 */
class ScheduledPostPublisherNoDuplicateTest {

    @Test
    void postProcessingFailureDoesNotRepublishAndCompletesThePost() {
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);
        PersonaRepository personas = mock(PersonaRepository.class);
        BotTokenCache tokens = mock(BotTokenCache.class);
        BackendBotClient backend = mock(BackendBotClient.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiScheduledPartnerAnswerRepository partnerRepo = mock(AiScheduledPartnerAnswerRepository.class);
        SourceReservationSupport reservations = mock(SourceReservationSupport.class);
        ScheduledPostTelegramNotifier notifier = mock(ScheduledPostTelegramNotifier.class);

        AiScheduledPost row = mock(AiScheduledPost.class);
        when(row.getId()).thenReturn("sched-11");
        when(row.getPersonaId()).thenReturn("p1");
        when(row.getTitle()).thenReturn("제목입니다");
        when(row.getBody()).thenReturn("본문입니다");
        when(row.getCategory()).thenReturn("COUPLE");
        when(row.getOrigin()).thenReturn("PAIRED");
        when(row.getCandidatesJson()).thenReturn("{}");
        when(row.getAttemptCount()).thenReturn(1);

        Persona author = Persona.builder()
                .id("p1").archetype("T").tier("REGULAR")
                .voiceProfile(Map.of()).interests(Map.of()).biasProfile(Map.of())
                .circadian(List.of()).slangLevel(new BigDecimal("0.3"))
                .active(true).createdAt(Instant.now())
                .build();
        when(personas.findById("p1")).thenReturn(Optional.of(author));
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("p1@againspring.internal");
        when(tokens.getToken(anyString(), anyString(), any())).thenReturn(Optional.of("jwt"));

        PostDto published = new PostDto();
        published.setId("post_abc");
        when(backend.createPost(anyString(), any())).thenReturn(Optional.of(published));

        // 후처리에서 터지는 상황을 만든다(상대방 답변 저장 실패).
        when(partnerRepo.save(any())).thenThrow(new IllegalStateException("db down"));

        ScheduledPostPublisher publisher = new ScheduledPostPublisher(
                leases, personas, tokens, backend, jdbc, new OrchestratorProperties(),
                mock(ThreadPlanService.class), mock(ThreadPlanGenerationService.class),
                new ObjectMapper(), partnerRepo, mock(CandidateScheduleSupport.class),
                reservations, notifier,
                ScheduledPostPublisherTestSupport.notBlockedConfigRepository());

        when(leases.claimById(eq("sched-11"), anyString(), any())).thenReturn(Optional.of(row));

        publisher.publishNow("sched-11", true);

        // 글은 정확히 한 번만 만들어진다.
        verify(backend, times(1)).createPost(anyString(), any());
        // 재시도 상태로 되돌리지 않는다 — 되돌리면 다음 틱이 다시 게시한다.
        verify(leases, never()).releaseFailed(eq("sched-11"), anyString(), anyString(), eq(true));
        // 발행은 성공으로 확정된다.
        verify(leases, times(1)).completePosted(eq("sched-11"), anyString(), eq("post_abc"));
        verify(reservations, times(1)).commitFromCandidatesJson(anyString());
    }
}
