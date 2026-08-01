package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiHumanInteractionInbox;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.HumanInteractionStatus;
import com.againspring.aiuser.orchestrator.repository.AiPostInterestedPersonaRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanItemRepository;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.safety.ContentSafetyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HumanReplyBatchServiceTest {

    @Mock private HumanInteractionInboxService inbox;
    @Mock private AiThreadPlanRepository plans;
    @Mock private AiThreadPlanItemRepository planItems;
    @Mock private PersonaRepository personaRepository;
    @Mock private AiPostInterestedPersonaRepository interestedPersonas;
    @Mock private LlmAiUserClient llm;
    @Mock private ContentSafetyGuard guard;
    @Mock private OrchestratorProperties props;
    @Mock private AiUserGenerationConfigRepository configRepository;
    @Mock private BackendBotClient backend;
    @Mock private JdbcTemplate jdbc;

    private HumanReplyBatchService service;
    private OrchestratorProperties.HumanReply humanReply;

    @BeforeEach
    void setUp() {
        humanReply = new OrchestratorProperties.HumanReply();
        humanReply.setDelayMinutesMin(1);
        humanReply.setDelayMinutesMax(30);
        humanReply.setChunkSize(20);
        humanReply.setRespondersPerInteractionMax(3);
        humanReply.setDistinctPersonasPerPostHumanMax(3);
        humanReply.setRepliesPerPersonaPerPostHumanMax(5);
        humanReply.setRepliesPerPostHumanMax(15);
        humanReply.setCandidateRespondersMax(8);
        lenient().when(props.getHumanReply()).thenReturn(humanReply);
        lenient().when(interestedPersonas.findByPostIdOrderByScoreDesc(anyString())).thenReturn(List.of());
        service = new HumanReplyBatchService(
                inbox, plans, planItems, personaRepository, interestedPersonas, llm, guard, props,
                configRepository, backend, jdbc);
    }

    @Test
    void buildItemInjectsBodiesAndCandidateRespondersWithStructuredVoice() {
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .parentCommentId("10").authorId("u1").interactionType("REPLY")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jdbc.queryForList(startsWith("SELECT pc.body"), eq(42L), eq("post-1")))
                .thenReturn(List.of(Map.of(
                        "body", "사람이 단 댓글 본문",
                        "parent_id", 10L,
                        "post_title", "사연 제목",
                        "post_body", "사연 본문입니다")));
        when(jdbc.queryForList(startsWith("SELECT body FROM post_comments"), eq(10L)))
                .thenReturn(List.of(Map.of("body", "부모 댓글")));
        when(planItems.findByPostAndTypesAndStatuses(any(), any(), any())).thenReturn(List.of());
        Map<String, Object> voice = Map.of("formality", "casual", "nickname", "봄이", "voice_type", "NATEPAN");
        Persona persona = Persona.builder().id("p-active").active(true).voiceProfile(voice).build();
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(persona));

        Optional<Map<String, Object>> item = service.buildItem(entry);

        assertThat(item).isPresent();
        assertThat(item.get().get("humanBody")).isEqualTo("사람이 단 댓글 본문");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) item.get().get("candidateResponders");
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).get("personaId")).isEqualTo("p-active");
        assertThat(candidates.get(0).get("formality")).isEqualTo("casual");
        assertThat(candidates.get(0).get("voiceProfile")).isInstanceOf(Map.class);
        assertThat(candidates.get(0).get("voiceProfile")).isEqualTo(voice);
        assertThat(candidates.get(0).get("voiceProfile")).isNotInstanceOf(String.class);
    }

    @Test
    void persistRejectsUnknownPersonaAndReleasesWhenLlmHadReplies() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .authorId("u1").interactionType("COMMENT")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(now).expiresAt(now.plusSeconds(3600)).leaseOwner("human-reply-batch")
                .build();
        when(personaRepository.existsById("ghost")).thenReturn(false);
        when(plans.findTopByPostIdOrderByPostRevisionDesc("post-1")).thenReturn(Optional.of(
                AiThreadPlan.builder().id("plan-1").postId("post-1")
                        .absoluteExpiresAt(now.plusSeconds(86400)).build()));

        service.persist("human-reply-batch", List.of(entry), Map.of(
                "replies", List.of(Map.of("humanCommentId", 42, "personaId", "ghost", "body", "답글"))), now);

        verify(planItems, never()).save(any());
        verify(inbox).release("inbox-1", "human-reply-batch");
    }

    @Test
    void persistMultiReplyUsesInboxPersonaIdempotencyKey() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .authorId("u1").interactionType("COMMENT")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(now).expiresAt(now.plusSeconds(3600)).leaseOwner("human-reply-batch")
                .build();
        when(plans.findTopByPostIdOrderByPostRevisionDesc("post-1")).thenReturn(Optional.of(
                AiThreadPlan.builder().id("plan-1").postId("post-1")
                        .absoluteExpiresAt(now.plusSeconds(86400)).build()));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(personaRepository.existsById("p2")).thenReturn(true);
        when(guard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(planItems.findHumanReplyItemsForPost(eq("post-1"), any())).thenReturn(List.of());
        when(planItems.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(planItems.save(any())).thenAnswer(inv -> {
            AiThreadPlanItem item = inv.getArgument(0);
            if (item.getId() == null) item.setId("item-" + item.getPersonaId());
            return item;
        });

        service.persist("human-reply-batch", List.of(entry), Map.of(
                "replies", List.of(
                        Map.of("humanCommentId", 42, "personaId", "p1", "body", "첫 답"),
                        Map.of("humanCommentId", 42, "personaId", "p2", "body", "둘째 답"))), now);

        ArgumentCaptor<AiThreadPlanItem> captor = ArgumentCaptor.forClass(AiThreadPlanItem.class);
        verify(planItems, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(AiThreadPlanItem::getIdempotencyKey)
                .containsExactlyInAnyOrder("human-reply:inbox-1:p1", "human-reply:inbox-1:p2");
        verify(inbox, times(1)).markResponded(eq("inbox-1"), eq("human-reply-batch"), anyString(), eq(1));
    }

    @Test
    void persistMarksNoResponseWhenLlmReturnsZeroReplies() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .authorId("u1").interactionType("COMMENT")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(now).expiresAt(now.plusSeconds(3600)).leaseOwner("human-reply-batch")
                .build();

        service.persist("human-reply-batch", List.of(entry), Map.of("replies", List.of()), now);

        verify(planItems, never()).save(any());
        verify(inbox).markSkipped("inbox-1", "human-reply-batch", HumanReplyBatchService.FAILURE_NO_RESPONSE);
        verify(inbox, never()).release(anyString(), anyString());
    }

    @Test
    void persistUsesConfiguredDelayRangeWhenLlmDelayAbsent() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .authorId("u1").interactionType("COMMENT")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(now).expiresAt(now.plusSeconds(3600)).leaseOwner("human-reply-batch")
                .build();
        when(plans.findTopByPostIdOrderByPostRevisionDesc("post-1")).thenReturn(Optional.of(
                AiThreadPlan.builder().id("plan-1").postId("post-1")
                        .absoluteExpiresAt(now.plusSeconds(86400)).build()));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(guard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(planItems.findHumanReplyItemsForPost(eq("post-1"), any())).thenReturn(List.of());
        when(planItems.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(planItems.save(any())).thenAnswer(inv -> {
            AiThreadPlanItem item = inv.getArgument(0);
            item.setId("item-1");
            return item;
        });

        service.persist("human-reply-batch", List.of(entry), Map.of(
                "replies", List.of(Map.of("humanCommentId", 42, "personaId", "p1", "body", "자연스러운 답"))), now);

        ArgumentCaptor<AiThreadPlanItem> captor = ArgumentCaptor.forClass(AiThreadPlanItem.class);
        verify(planItems).save(captor.capture());
        Duration delay = Duration.between(now, captor.getValue().getScheduledAt());
        assertThat(delay.toMinutes()).isBetween(1L, 30L);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("human-reply:inbox-1:p1");
        verify(inbox).markResponded("inbox-1", "human-reply-batch", "item-1", 1);
    }

    @Test
    void persistClearsErrorLedgerOnSuccessWithAttemptCount() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .authorId("u1").interactionType("COMMENT")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(now).expiresAt(now.plusSeconds(3600)).leaseOwner("human-reply-batch")
                .attemptCount(0).lastErrorCode("GENERATION_FAILED")
                .build();
        when(plans.findTopByPostIdOrderByPostRevisionDesc("post-1")).thenReturn(Optional.of(
                AiThreadPlan.builder().id("plan-1").postId("post-1")
                        .absoluteExpiresAt(now.plusSeconds(86400)).build()));
        when(personaRepository.existsById("p1")).thenReturn(true);
        when(guard.check(any(), any())).thenReturn(ContentSafetyGuard.GuardResult.ok());
        when(planItems.findHumanReplyItemsForPost(eq("post-1"), any())).thenReturn(List.of());
        when(planItems.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(planItems.save(any())).thenAnswer(inv -> {
            AiThreadPlanItem item = inv.getArgument(0);
            item.setId("item-1");
            return item;
        });

        service.persist("human-reply-batch", List.of(entry), Map.of(
                "replies", List.of(Map.of("humanCommentId", 42, "personaId", "p1", "body", "재시도 성공"))),
                now, 2);

        verify(inbox).markResponded("inbox-1", "human-reply-batch", "item-1", 2);
    }

    @Test
    void runMarksSkippedGenerationFailedAfterTwoEmptyLlmAttempts() {
        OrchestratorProperties.ThreadPlan threadPlan = new OrchestratorProperties.ThreadPlan();
        threadPlan.setEnabled(true);
        threadPlan.setHumanReplyBatchEnabled(true);
        when(props.isEnabled()).thenReturn(true);
        when(props.getThreadPlan()).thenReturn(threadPlan);
        var config = mock(com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig.class);
        when(config.isAiUserKillSwitch()).thenReturn(false);
        when(config.getProviderHumanInteraction()).thenReturn("CLAUDE");
        when(config.getHumanBatchMaxInteractions()).thenReturn(0);
        when(config.getHumanBatchMaxPosts()).thenReturn(0);
        when(configRepository.findById(1)).thenReturn(Optional.of(config));

        Instant now = Instant.now();
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .authorId("u1").interactionType("COMMENT")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(now).expiresAt(now.plusSeconds(3600)).leaseOwner("human-reply-batch")
                .build();
        when(inbox.claimPending(eq("human-reply-batch"), anyInt(), any(), any())).thenReturn(List.of(entry));
        when(plans.findTopByPostIdOrderByPostRevisionDesc("post-1")).thenReturn(Optional.of(
                AiThreadPlan.builder().id("plan-1").postId("post-1")
                        .absoluteExpiresAt(now.plusSeconds(86400)).build()));
        when(jdbc.queryForList(startsWith("SELECT pc.body"), eq(42L), eq("post-1")))
                .thenReturn(List.of(Map.of(
                        "body", "사람 댓글",
                        "post_title", "제목",
                        "post_body", "본문")));
        when(planItems.findByPostAndTypesAndStatuses(any(), any(), any())).thenReturn(List.of());
        Persona persona = Persona.builder().id("p1").active(true)
                .voiceProfile(Map.of("formality", "neutral", "nickname", "봄")).build();
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(persona));
        when(llm.generateHumanReplies(any())).thenReturn(Optional.empty());

        service.run();

        verify(llm, times(HumanReplyBatchService.AUTOMATIC_ATTEMPTS_MAX)).generateHumanReplies(any());
        verify(inbox).markSkipped("inbox-1", "human-reply-batch",
                HumanReplyBatchService.FAILURE_GENERATION_FAILED, 2);
        verify(inbox, never()).release(eq("inbox-1"), any());
    }

    @Test
    void chunkIndexesSplitsByConfiguredSize() {
        assertThat(HumanReplyBatchService.chunkIndexes(45, 20))
                .containsExactly(List.of(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19),
                        List.of(20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39),
                        List.of(40,41,42,43,44));
        assertThat(HumanReplyBatchService.chunkIndexes(0, 20)).isEmpty();
        assertThat(HumanReplyBatchService.chunkIndexes(3, 20)).containsExactly(List.of(0, 1, 2));
    }

    @Test
    void resolveDelayHonorsLlmValueInsideRange() {
        assertThat(service.resolveDelay(12).toMinutes()).isEqualTo(12);
    }

    @Test
    void resolveDelayFallsBackWhenLlmOutOfRange() {
        long minutes = service.resolveDelay(99).toMinutes();
        assertThat(minutes).isBetween(1L, 30L);
    }
}
