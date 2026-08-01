package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.enums.ThreadPlanStatus;
import com.againspring.aiuser.orchestrator.repository.AiThreadPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HumanReplyTtlCleanupServiceTest {

    @Mock private OrchestratorProperties props;
    @Mock private HumanInteractionInboxService inboxService;
    @Mock private AiThreadPlanRepository planRepository;

    private HumanReplyTtlCleanupService service;
    private OrchestratorProperties.HumanReply humanReply;

    @BeforeEach
    void setUp() {
        humanReply = new OrchestratorProperties.HumanReply();
        humanReply.setInboxTtlDays(7);
        humanReply.setPlanTtlDays(7);
        humanReply.setTtlCleanupEnabled(false);
        when(props.getHumanReply()).thenReturn(humanReply);
        service = new HumanReplyTtlCleanupService(props, inboxService, planRepository);
    }

    @Test
    void skipsWhenFlagOffAndNotForced() {
        var result = service.run(Instant.parse("2026-08-01T12:00:00Z"), false);
        assertThat(result.ran()).isFalse();
        verifyNoInteractions(inboxService, planRepository);
    }

    @Test
    void forceRunsEvenWhenFlagOff() {
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        when(inboxService.reclaimExpiredProcessing(any())).thenReturn(35);
        when(inboxService.cancelExpiredByObservedAt(any())).thenReturn(300);
        when(planRepository.expireRequestedOlderThan(any(), any(), any(), any())).thenReturn(100);

        var result = service.run(now, true);

        assertThat(result.ran()).isTrue();
        assertThat(result.reclaimedProcessing()).isEqualTo(35);
        assertThat(result.inboxCancelled()).isEqualTo(300);
        assertThat(result.plansExpired()).isEqualTo(100);
        verify(inboxService).cancelExpiredByObservedAt(now.minus(7, ChronoUnit.DAYS));
        verify(planRepository).expireRequestedOlderThan(
                eq(ThreadPlanStatus.REQUESTED),
                eq(ThreadPlanStatus.EXPIRED),
                eq(HumanInteractionInboxService.REASON_EXPIRED_TTL),
                eq(now.minus(7, ChronoUnit.DAYS)));
    }

    @Test
    void enabledFlagRunsWithoutForce() {
        humanReply.setTtlCleanupEnabled(true);
        Instant now = Instant.parse("2026-08-01T12:00:00Z");
        when(inboxService.reclaimExpiredProcessing(any())).thenReturn(0);
        when(inboxService.cancelExpiredByObservedAt(any())).thenReturn(0);
        when(planRepository.expireRequestedOlderThan(any(), any(), any(), any())).thenReturn(0);

        assertThat(service.run(now, false).ran()).isTrue();
        verify(inboxService).reclaimExpiredProcessing(any());
    }
}
