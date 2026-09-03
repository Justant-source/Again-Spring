package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduledPostPublisherPublishNowTest {

    @Test
    void notDueWithoutForceReturnsEmpty() {
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);
        AiScheduledPost row = AiScheduledPost.builder()
                .id("s1")
                .personaId("p1")
                .title("t")
                .body("b")
                .scheduledPublishAt(Instant.now().plusSeconds(3600))
                .status(ScheduledPostStatus.SCHEDULED)
                .build();
        when(leases.claimById("s1", "scheduled-post-publisher", java.time.Duration.ofMinutes(5))).thenReturn(Optional.of(row));
        ScheduledPostPublisher pub = ScheduledPostPublisherTestSupport.withLeases(leases);
        assertTrue(pub.publishNow("s1", false).isEmpty());
        verify(leases).release("s1", "scheduled-post-publisher");
    }

    @Test
    void unknownIdIsEmpty() {
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);
        when(leases.claimById(eq("nope"), anyString(), any())).thenReturn(Optional.empty());
        assertTrue(ScheduledPostPublisherTestSupport.withLeases(leases).publishNow("nope", true).isEmpty());
    }

    /**
     * 결함 1 감사: 관리자 kill 스위치는 publishDue()뿐 아니라 publishNow()에서도 존중돼야 한다.
     * force=true는 슬롯 시각·QuietHours만 건너뛴다 — kill 스위치는 절대 우회하지 않는다.
     */
    @Test
    void killSwitchBlocksEvenWithForceTrue() {
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);
        AiScheduledPost row = AiScheduledPost.builder()
                .id("s1").personaId("p1").title("t").body("b")
                .scheduledPublishAt(Instant.now().plusSeconds(3600))
                .status(ScheduledPostStatus.SCHEDULED)
                .build();
        when(leases.claimById("s1", "scheduled-post-publisher", java.time.Duration.ofMinutes(5))).thenReturn(Optional.of(row));

        AiUserGenerationConfigRepository configRepo = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig cfg = mock(AiUserGenerationConfig.class);
        when(cfg.isAiUserKillSwitch()).thenReturn(true);
        when(configRepo.findById(1)).thenReturn(Optional.of(cfg));

        ScheduledPostPublisher pub = ScheduledPostPublisherTestSupport.withLeasesAndConfig(leases, configRepo);
        assertTrue(pub.publishNow("s1", true).isEmpty(), "force=true must not bypass the admin kill switch");
        verify(leases).release("s1", "scheduled-post-publisher");
    }

    @Test
    void pausedBlocksEvenWithForceTrue() {
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);
        AiScheduledPost row = AiScheduledPost.builder()
                .id("s2").personaId("p1").title("t").body("b")
                .scheduledPublishAt(Instant.now().plusSeconds(3600))
                .status(ScheduledPostStatus.SCHEDULED)
                .build();
        when(leases.claimById("s2", "scheduled-post-publisher", java.time.Duration.ofMinutes(5))).thenReturn(Optional.of(row));

        AiUserGenerationConfigRepository configRepo = mock(AiUserGenerationConfigRepository.class);
        AiUserGenerationConfig cfg = mock(AiUserGenerationConfig.class);
        when(cfg.isScheduleExecutionPaused()).thenReturn(true);
        when(configRepo.findById(1)).thenReturn(Optional.of(cfg));

        ScheduledPostPublisher pub = ScheduledPostPublisherTestSupport.withLeasesAndConfig(leases, configRepo);
        assertTrue(pub.publishNow("s2", true).isEmpty(), "force=true must not bypass schedule_execution_paused");
        verify(leases).release("s2", "scheduled-post-publisher");
    }

    @Test
    void missingConfigRowFailsClosedEvenWithForce() {
        ScheduledPostLeaseService leases = mock(ScheduledPostLeaseService.class);
        AiScheduledPost row = AiScheduledPost.builder()
                .id("s3").personaId("p1").title("t").body("b")
                .scheduledPublishAt(Instant.now().plusSeconds(3600))
                .status(ScheduledPostStatus.SCHEDULED)
                .build();
        when(leases.claimById("s3", "scheduled-post-publisher", java.time.Duration.ofMinutes(5))).thenReturn(Optional.of(row));

        AiUserGenerationConfigRepository configRepo = mock(AiUserGenerationConfigRepository.class);
        when(configRepo.findById(1)).thenReturn(Optional.empty());

        ScheduledPostPublisher pub = ScheduledPostPublisherTestSupport.withLeasesAndConfig(leases, configRepo);
        assertTrue(pub.publishNow("s3", true).isEmpty(), "missing config row must fail closed, even with force=true");
        verify(leases).release("s3", "scheduled-post-publisher");
    }
}
