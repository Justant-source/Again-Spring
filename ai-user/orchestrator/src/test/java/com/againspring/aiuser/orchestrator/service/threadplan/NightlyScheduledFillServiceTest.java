package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiUserMlClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.notification.ScheduledPostTelegramNotifier;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.scheduler.PairedPostScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class NightlyScheduledFillServiceTest {

    @Mock private AiPostBundleService bundleService;
    @Mock private PairedPostScheduler pairedPostScheduler;
    @Mock private PersonaRepository personaRepository;
    @Mock private AiUserGenerationConfigRepository configRepository;
    @Mock private ScheduledPostTelegramNotifier telegramNotifier;
    @Mock private AiUserMlClient aiUserMlClient;

    private NightlyScheduledFillService service;
    private OrchestratorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OrchestratorProperties();
        service = new NightlyScheduledFillService(
                bundleService, pairedPostScheduler, personaRepository, configRepository,
                properties, telegramNotifier, aiUserMlClient);
        when(pairedPostScheduler.tryHoldPairs(any(Integer.class), any(), any()))
                .thenReturn(new PairedPostScheduler.PairHoldBatch(0, 0, List.of()));
        // By default, all (source, plaza) pairs have inventory (graceful degradation on ML client errors)
        when(aiUserMlClient.getAvailableCount(anyString(), anyString(), anyInt()))
                .thenReturn(10);
    }

    @Test
    void emptyClaimRetriesOtherSourceBlindToNatepan() {
        Persona blind = persona("p-blind", "BLIND");
        Persona nate = persona("p-nate", "NATEPAN");
        Instant slot = Instant.now().plusSeconds(3600);
        LlmCallBudget budget = LlmCallBudget.ofMultiplier(1, 3);

        when(bundleService.generateAndHoldResult(any(), anyString(), any(), anyString(), any(), anyString(), anySet()))
                .thenAnswer(inv -> {
                    Persona author = inv.getArgument(0);
                    String plaza = inv.getArgument(1);
                    String source = inv.getArgument(5);
                    if (SourceMixPlanner.SOURCE_BLIND.equals(source)) {
                        return HoldResult.claimEmpty(source, plaza, author.getId(), "no claimed source");
                    }
                    return HoldResult.saved(held("saved-nate"), source, plaza, author.getId(), 7L);
                });

        NightlyScheduledFillService.FillResult result = service.fillSoloWithPlan(
                1, List.of("blind"), List.of(slot), new ArrayList<>(List.of(blind, nate)),
                budget, new ArrayList<>(), new ArrayList<>(), new Random(1));

        assertThat(result.soloSaved()).isEqualTo(1);
        assertThat(result.scheduledIds()).containsExactly("saved-nate");
        ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
        verify(bundleService, org.mockito.Mockito.atLeast(2)).generateAndHoldResult(
                any(), anyString(), any(), anyString(), any(), sourceCaptor.capture(), anySet());
        assertThat(sourceCaptor.getAllValues()).contains("blind", "natepan");
        verify(telegramNotifier, never()).nightlyShortfall(any(Integer.class), any(Integer.class),
                any(Integer.class), any(Integer.class), any());
    }

    @Test
    void llmFailDoesNotReuseSameExampleOnNextAttempt() {
        Persona nate = persona("p-nate", "NATEPAN");
        Instant slot = Instant.now().plusSeconds(3600);
        LlmCallBudget budget = LlmCallBudget.ofMultiplier(1, 3);
        AtomicInteger calls = new AtomicInteger();

        when(bundleService.generateAndHoldResult(any(), anyString(), any(), anyString(), any(), anyString(), anySet()))
                .thenAnswer(inv -> {
                    Set<Long> skip = inv.getArgument(6);
                    String plaza = inv.getArgument(1);
                    String source = inv.getArgument(5);
                    int n = calls.getAndIncrement();
                    if (n == 0) {
                        assertThat(skip).doesNotContain(99L);
                        return HoldResult.llmOrSafety(source, plaza, "p-nate", 99L, "unsafe post");
                    }
                    assertThat(skip).contains(99L);
                    return HoldResult.saved(held("saved-2"), source, plaza, "p-nate", 100L);
                });

        NightlyScheduledFillService.FillResult result = service.fillSoloWithPlan(
                1, List.of("natepan"), List.of(slot), new ArrayList<>(List.of(nate)),
                budget, new ArrayList<>(), new ArrayList<>(), new Random(1));

        assertThat(result.soloSaved()).isEqualTo(1);
        assertThat(result.llmUsed()).isEqualTo(2);
        verify(bundleService, times(2)).generateAndHoldResult(any(), anyString(), any(), anyString(), any(),
                anyString(), anySet());
    }

    @Test
    void stopsAtThreeTimesNLlmInvocations() {
        Persona nate = persona("p-nate", "NATEPAN");
        Instant slot = Instant.now().plusSeconds(3600);
        LlmCallBudget budget = LlmCallBudget.ofMultiplier(1, 3);
        AtomicLong example = new AtomicLong(1);

        when(bundleService.generateAndHoldResult(any(), anyString(), any(), anyString(), any(), anyString(), anySet()))
                .thenAnswer(inv -> HoldResult.llmOrSafety(
                        inv.getArgument(5), inv.getArgument(1), "p-nate",
                        example.getAndIncrement(), "LLM empty"));

        NightlyScheduledFillService.FillResult result = service.fillSoloWithPlan(
                1, List.of("natepan"), List.of(slot), new ArrayList<>(List.of(nate)),
                budget, new ArrayList<>(), new ArrayList<>(), new Random(1));

        assertThat(result.soloSaved()).isZero();
        assertThat(result.llmUsed()).isEqualTo(3);
        verify(bundleService, times(3)).generateAndHoldResult(any(), anyString(), any(), anyString(), any(),
                anyString(), anySet());
    }

    @Test
    void telegramNotCalledWhenSavedEqualsN() {
        stubConfig(1, 0.0);
        NightlyScheduledFillService spy = spy(service);
        doReturn(new NightlyScheduledFillService.FillResult(
                1, 1, 1, 1, 3, 0, 1, List.of("ok"), List.of(), null))
                .when(spy).fillSolo(eq(1), anyInt(), anyInt(), anyLong(), any(), any(), any());

        NightlyScheduledFillService.FillResult result = spy.fillNightly(0, 23, 15, true);

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.target()).isEqualTo(1);
        verify(telegramNotifier, never()).nightlyShortfall(any(Integer.class), any(Integer.class),
                any(Integer.class), any(Integer.class), any());
    }

    @Test
    void telegramCalledWhenSavedBelowN() {
        stubConfig(1, 0.0);
        NightlyScheduledFillService spy = spy(service);
        List<NightlySlotFailure> failures = new ArrayList<>();
        failures.add(new NightlySlotFailure("solo", "natepan", "FAMILY", "p-nate",
                HoldResult.Outcome.LLM_OR_SAFETY, "outcome=LLM_OR_SAFETY safety blocked"));
        doReturn(new NightlyScheduledFillService.FillResult(
                1, 0, 3, 3, 3, 0, 0, List.of(), failures, null))
                .when(spy).fillSolo(eq(1), anyInt(), anyInt(), anyLong(), any(), any(), any());

        NightlyScheduledFillService.FillResult result = spy.fillNightly(0, 23, 15, true);

        assertThat(result.saved()).isZero();
        verify(telegramNotifier).nightlyShortfall(eq(1), eq(0), anyInt(), eq(3), any());
    }

    @Test
    void plazaRetryOrderPutsTopInterestFirstThenOtherLast() {
        Persona p = persona("p1", "NATEPAN");
        p.setInterests(Map.of("WORK", 0.9, "FAMILY", 0.1));
        List<String> order = PlazaGrounding.retryOrder(p);
        assertThat(order.get(0)).isEqualTo("WORK");
        assertThat(order).containsExactly("WORK", "COUPLE", "MARRIED", "FRIEND", "FAMILY", "OTHER");
    }

    @Test
    void precomputeEmptyPairsSkipsZeroInventoryPlazas() {
        Persona nate = persona("p-nate", "NATEPAN");
        Instant slot = Instant.now().plusSeconds(3600);
        LlmCallBudget budget = LlmCallBudget.ofMultiplier(1, 3);

        // For NATEPAN source: MARRIED has 10, FAMILY has 0, all others have 10
        when(aiUserMlClient.getAvailableCount("natepan", "MARRIED", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("natepan", "FAMILY", 14)).thenReturn(0);
        when(aiUserMlClient.getAvailableCount("natepan", "COUPLE", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("natepan", "WORK", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("natepan", "FRIEND", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("natepan", "OTHER", 14)).thenReturn(10);
        // BLIND source
        when(aiUserMlClient.getAvailableCount("blind", "MARRIED", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("blind", "FAMILY", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("blind", "COUPLE", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("blind", "WORK", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("blind", "FRIEND", 14)).thenReturn(10);
        when(aiUserMlClient.getAvailableCount("blind", "OTHER", 14)).thenReturn(10);

        AtomicInteger bundleCallCount = new AtomicInteger(0);
        when(bundleService.generateAndHoldResult(any(), anyString(), any(), anyString(), any(), anyString(), anySet()))
                .thenAnswer(inv -> {
                    bundleCallCount.incrementAndGet();
                    String plaza = inv.getArgument(1);
                    // First attempt should be on a NATEPAN plaza that has inventory (not FAMILY)
                    return HoldResult.saved(held("saved-" + bundleCallCount.get()),
                            inv.getArgument(5), plaza, "p-nate", 1L);
                });

        NightlyScheduledFillService.FillResult result = service.fillSoloWithPlan(
                1, List.of("natepan"), List.of(slot), new ArrayList<>(List.of(nate)),
                budget, new ArrayList<>(), new ArrayList<>(), new Random(1));

        assertThat(result.soloSaved()).isEqualTo(1);
        // skipped should include the FAMILY plaza that was skipped
        assertThat(result.skipped()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void mlClientErrorGracefullyAssumesNonEmpty() {
        Persona nate = persona("p-nate", "NATEPAN");
        Instant slot = Instant.now().plusSeconds(3600);
        LlmCallBudget budget = LlmCallBudget.ofMultiplier(1, 3);

        // ML client throws exception (e.g., network error) — graceful degradation returns 0 from getAvailableCount
        doThrow(new RuntimeException("Network error")).when(aiUserMlClient)
                .getAvailableCount(anyString(), anyString(), anyInt());

        when(bundleService.generateAndHoldResult(any(), anyString(), any(), anyString(), any(), anyString(), anySet()))
                .thenAnswer(inv -> HoldResult.saved(held("saved"),
                        inv.getArgument(5), inv.getArgument(1), "p-nate", 1L));

        // Should not crash; graceful degradation: assume not empty, attempt claim
        NightlyScheduledFillService.FillResult result = service.fillSoloWithPlan(
                1, List.of("natepan"), List.of(slot), new ArrayList<>(List.of(nate)),
                budget, new ArrayList<>(), new ArrayList<>(), new Random(1));

        assertThat(result.soloSaved()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
    }

    @Test
    void skipCountTrackedSeparatelyFromFailures() {
        Persona nate = persona("p-nate", "NATEPAN");
        Instant slot = Instant.now().plusSeconds(3600);
        LlmCallBudget budget = LlmCallBudget.ofMultiplier(1, 3);

        // Set specific return values for natepan plazas (all zero), blind plazas (all 10)
        doReturn(0).when(aiUserMlClient).getAvailableCount(eq("natepan"), anyString(), anyInt());
        doReturn(10).when(aiUserMlClient).getAvailableCount(eq("blind"), anyString(), anyInt());

        List<NightlySlotFailure> failures = new ArrayList<>();
        NightlyScheduledFillService.FillResult result = service.fillSoloWithPlan(
                1, List.of("natepan"), List.of(slot), new ArrayList<>(List.of(nate)),
                budget, failures, new ArrayList<>(), new Random(1));

        // Should have skipped all natepan plazas, retry with blind
        assertThat(result.skipped()).isGreaterThan(0);
        // Failures should be empty or only from other sources (not from skipped pairs)
        assertThat(failures).allMatch(f -> !f.detail().contains("SKIP_NO_INVENTORY"));
    }

    private void stubConfig(int targetPosts, double pairedShare) {
        try {
            var ctor = AiUserGenerationConfig.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AiUserGenerationConfig c = ctor.newInstance();
            var posts = AiUserGenerationConfig.class.getDeclaredField("targetPosts");
            posts.setAccessible(true);
            posts.set(c, targetPosts);
            var share = AiUserGenerationConfig.class.getDeclaredField("nightlyPairedShare");
            share.setAccessible(true);
            share.set(c, pairedShare);
            when(configRepository.findById(1)).thenReturn(java.util.Optional.of(c));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Persona persona(String id, String voiceType) {
        return Persona.builder()
                .id(id)
                .archetype("ARCH")
                .tier("HEAVY")
                .voiceProfile(Map.of("formality", "casual", "voice_type", voiceType))
                .interests(Map.of("FAMILY", 0.9))
                .biasProfile(Map.of())
                .circadian(List.of())
                .slangLevel(new BigDecimal("0.40"))
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    private static AiScheduledPost held(String id) {
        return AiScheduledPost.builder()
                .id(id)
                .personaId("p-nate")
                .category("FAMILY")
                .title("제목")
                .body("본문")
                .candidatesJson("{}")
                .scheduledPublishAt(Instant.now())
                .status(ScheduledPostStatus.SCHEDULED)
                .build();
    }
}
