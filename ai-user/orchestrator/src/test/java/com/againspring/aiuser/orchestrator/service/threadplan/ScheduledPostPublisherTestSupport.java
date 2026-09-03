package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.notification.ScheduledPostTelegramNotifier;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPartnerAnswerRepository;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;

/**
 * Test-only helper for {@link ScheduledPostPublisherPublishNowTest}: builds a real
 * {@link ScheduledPostPublisher} with every constructor dependency mocked (or a harmless real
 * instance) except {@code leases}, which the caller supplies and stubs directly.
 */
final class ScheduledPostPublisherTestSupport {
    private ScheduledPostPublisherTestSupport() { }

    static ScheduledPostPublisher withLeases(ScheduledPostLeaseService leases) {
        return new ScheduledPostPublisher(
                leases,
                mock(PersonaRepository.class),
                mock(BotTokenCache.class),
                mock(BackendBotClient.class),
                mock(JdbcTemplate.class),
                new OrchestratorProperties(),
                mock(ThreadPlanService.class),
                mock(ThreadPlanGenerationService.class),
                new ObjectMapper(),
                mock(AiScheduledPartnerAnswerRepository.class),
                mock(CandidateScheduleSupport.class),
                mock(SourceReservationSupport.class),
                mock(ScheduledPostTelegramNotifier.class),
                mock(AiUserGenerationConfigRepository.class));
    }
}
