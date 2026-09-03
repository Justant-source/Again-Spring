package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.engine.ViewDispatcher;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPostRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserRuntimeRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.scheduler.PairedPostScheduler;
import com.againspring.aiuser.orchestrator.service.capsule.PersonaCapsuleService;
import com.againspring.aiuser.orchestrator.service.engagement.PlanEngagementDispatcher;
import com.againspring.aiuser.orchestrator.service.gate.EffectiveGatesService;
import com.againspring.aiuser.orchestrator.service.llm.LlmGenerationGateService;
import com.againspring.aiuser.orchestrator.service.match.PersonaMatcherService;
import com.againspring.aiuser.orchestrator.service.persona.PersonaAutoProvisionService;
import com.againspring.aiuser.orchestrator.service.storyprofile.StoryProfileAnalyzer;
import com.againspring.aiuser.orchestrator.service.threadplan.HumanReplyTtlCleanupService;
import com.againspring.aiuser.orchestrator.service.threadplan.LlmCallBudget;
import com.againspring.aiuser.orchestrator.service.threadplan.NightlyScheduledFillService;
import com.againspring.aiuser.orchestrator.service.threadplan.ScheduledPostPublisher;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanGenerationService;
import com.againspring.aiuser.orchestrator.task.ActionExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTriggerControllerFillTest {

    @Mock private PairedPostScheduler pairedPostScheduler;
    @Mock private AiUserRuntimeRepository runtimeRepo;
    @Mock private PersonaRepository personaRepo;
    @Mock private ActionExecutor actionExecutor;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private OrchestratorProperties properties;
    @Mock private NightlyScheduledFillService nightlyScheduledFillService;
    @Mock private AiScheduledPostRepository scheduledPostRepository;
    @Mock private PlanEngagementDispatcher engagementDispatcher;
    @Mock private ViewDispatcher viewDispatcher;
    @Mock private HumanReplyTtlCleanupService humanReplyTtlCleanupService;
    @Mock private PersonaCapsuleService personaCapsuleService;
    @Mock private PersonaMatcherService personaMatcherService;
    @Mock private PersonaAutoProvisionService personaAutoProvisionService;
    @Mock private StoryProfileAnalyzer storyProfileAnalyzer;
    @Mock private ThreadPlanGenerationService threadPlanGenerationService;
    @Mock private LlmGenerationGateService llmGenerationGateService;
    @Mock private EffectiveGatesService effectiveGatesService;
    @Mock private ScheduledPostPublisher scheduledPostPublisher;

    private AdminTriggerController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminTriggerController(
                pairedPostScheduler, runtimeRepo, personaRepo, actionExecutor, jdbcTemplate,
                properties, nightlyScheduledFillService, scheduledPostRepository, engagementDispatcher,
                viewDispatcher, humanReplyTtlCleanupService, personaCapsuleService, personaMatcherService,
                personaAutoProvisionService, storyProfileAnalyzer, threadPlanGenerationService,
                llmGenerationGateService, effectiveGatesService, scheduledPostPublisher);
    }

    @Test
    void generateScheduledPostsUsesSoloFillWithThreeTimesCountCap() {
        NightlyScheduledFillService.FillResult filled = new NightlyScheduledFillService.FillResult(
                4, 4, 4, 4, 12, 0, 4, List.of("a"), List.of(), null);
        when(nightlyScheduledFillService.fillSolo(eq(4), eq(8), eq(22), eq(45L),
                org.mockito.ArgumentMatchers.any(), eq(false)))
                .thenReturn(filled);

        var response = controller.generateScheduledPosts(4, 8, 22, 45, false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("saved", 4);
        assertThat(response.getBody()).containsEntry("llmMax", 12);

        ArgumentCaptor<LlmCallBudget> cap = ArgumentCaptor.forClass(LlmCallBudget.class);
        verify(nightlyScheduledFillService).fillSolo(eq(4), eq(8), eq(22), eq(45L), cap.capture(), eq(false));
        assertThat(cap.getValue().max()).isEqualTo(12);
    }

    @Test
    void generateScheduledPostsPassesSkipSourceClaimThrough() {
        NightlyScheduledFillService.FillResult filled = new NightlyScheduledFillService.FillResult(
                2, 2, 2, 2, 6, 0, 2, List.of("a"), List.of(), null);
        when(nightlyScheduledFillService.fillSolo(eq(2), eq(8), eq(22), eq(45L),
                org.mockito.ArgumentMatchers.any(), eq(true)))
                .thenReturn(filled);

        var response = controller.generateScheduledPosts(2, 8, 22, 45, true);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(nightlyScheduledFillService).fillSolo(eq(2), eq(8), eq(22), eq(45L),
                org.mockito.ArgumentMatchers.any(), eq(true));
    }

    @Test
    void publishScheduledPostReturnsOkWithPostIdWhenPublished() {
        when(scheduledPostPublisher.publishNow("s1", true)).thenReturn(java.util.Optional.of("post-1"));

        var response = controller.publishScheduledPost("s1", true);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("scheduledId", "s1");
        assertThat(response.getBody()).containsEntry("postId", "post-1");
    }

    @Test
    void publishScheduledPostReturns409WhenNotDueOrNotFound() {
        when(scheduledPostPublisher.publishNow("s2", false)).thenReturn(java.util.Optional.empty());

        var response = controller.publishScheduledPost("s2", false);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "NOT_DUE_OR_NOT_FOUND");
        assertThat(response.getBody()).containsEntry("scheduledId", "s2");
    }

    @Test
    void fillNightlyScheduledPostsDelegatesWithTelegramFlag() {
        NightlyScheduledFillService.FillResult filled = new NightlyScheduledFillService.FillResult(
                5, 5, 6, 6, 15, 1, 4, List.of("a", "b"), List.of(), null);
        when(nightlyScheduledFillService.fillNightly(anyInt(), anyInt(), anyLong(), anyBoolean())).thenReturn(filled);

        var response = controller.fillNightlyScheduledPosts(8, 22, 45);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("target", 5);
        assertThat(response.getBody()).containsEntry("pairedSaved", 1);
        verify(nightlyScheduledFillService).fillNightly(8, 22, 45, true);
    }
}
