package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
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
}
