package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScheduledPostPublisher#publishDue()} must not claim due items when the admin DB
 * kill-switch / schedule-pause is on, mirroring {@link ThreadPlanPublisher} and
 * {@code PlanEngagementDispatcher}. AiUserGenerationConfig has no Lombok setter
 * (JPA @Immutable read-only mapping), so the config row is mocked rather than constructed.
 */
class ScheduledPostPublisherGateTest {
    @Test
    void killSwitchStopsScheduledPublishing() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getThreadPlan().setEnabled(true);
        props.getThreadPlan().setScheduledPostPublisherEnabled(true);
        AiUserGenerationConfigRepository configRepo = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig cfg = mock(AiUserGenerationConfig.class);
        when(cfg.isAiUserKillSwitch()).thenReturn(true);
        when(configRepo.findById(1)).thenReturn(Optional.of(cfg));
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);

        ScheduledPostPublisher p = new ScheduledPostPublisher(leases, null, null, null, null, props, null, null, null, null, null, null, null, configRepo);
        p.publishDue();

        verify(leases, never()).claimDue(anyString(), anyInt(), any(), any());
    }

    @Test
    void missingConfigRowFailsClosed() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setEnabled(true);
        props.getThreadPlan().setEnabled(true);
        props.getThreadPlan().setScheduledPostPublisherEnabled(true);
        AiUserGenerationConfigRepository configRepo = mock(AiUserGenerationConfigRepository.class);
        when(configRepo.findById(1)).thenReturn(Optional.empty());
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);

        ScheduledPostPublisher p = new ScheduledPostPublisher(leases, null, null, null, null, props, null, null, null, null, null, null, null, configRepo);
        p.publishDue();

        verify(leases, never()).claimDue(anyString(), anyInt(), any(), any());
    }
}
