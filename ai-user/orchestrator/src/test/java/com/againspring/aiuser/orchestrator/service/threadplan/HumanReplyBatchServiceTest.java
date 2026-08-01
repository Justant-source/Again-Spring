package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiHumanInteractionInbox;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlanItem;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.HumanInteractionStatus;
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
        lenient().when(props.getHumanReply()).thenReturn(humanReply);
        service = new HumanReplyBatchService(
                inbox, plans, planItems, personaRepository, llm, guard, props,
                configRepository, backend, jdbc);
    }

    @Test
    void buildItemInjectsRealBodiesAndResponder() {
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
        Persona persona = Persona.builder().id("p-active").active(true)
                .voiceProfile(Map.of("formality", "casual", "nickname", "봄이")).build();
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(persona));

        Optional<Map<String, Object>> item = service.buildItem(entry);

        assertThat(item).isPresent();
        assertThat(item.get().get("humanBody")).isEqualTo("사람이 단 댓글 본문");
        assertThat(item.get().get("postTitle")).isEqualTo("사연 제목");
        assertThat(item.get().get("postBody")).isEqualTo("사연 본문입니다");
        assertThat(item.get().get("parentBody")).isEqualTo("부모 댓글");
        @SuppressWarnings("unchecked")
        Map<String, Object> responder = (Map<String, Object>) item.get().get("responder");
        assertThat(responder.get("personaId")).isEqualTo("p-active");
        assertThat(responder.get("formality")).isEqualTo("casual");
    }

    @Test
    void persistRejectsUnknownPersona() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        AiHumanInteractionInbox entry = AiHumanInteractionInbox.builder()
                .id("inbox-1").postId("post-1").sourceCommentId("42")
                .authorId("u1").interactionType("COMMENT")
                .status(HumanInteractionStatus.PROCESSING)
                .observedAt(now).expiresAt(now.plusSeconds(3600)).leaseOwner("human-reply-batch")
                .build();
        when(personaRepository.existsById("ghost")).thenReturn(false);

        service.persist("human-reply-batch", List.of(entry), Map.of(
                "replies", List.of(Map.of("humanCommentId", 42, "personaId", "ghost", "body", "답글"))), now);

        verify(planItems, never()).save(any());
        verify(inbox).release("inbox-1", "human-reply-batch");
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
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("human-reply:42");
        verify(inbox).markResponded("inbox-1", "human-reply-batch", "item-1");
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
