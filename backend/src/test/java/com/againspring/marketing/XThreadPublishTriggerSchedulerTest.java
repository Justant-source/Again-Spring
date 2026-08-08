package com.againspring.marketing;

import com.againspring.marketing.holding.MarketingHoldingCommitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scheduler gate tests — commit pipeline lives in {@link MarketingHoldingCommitService}.
 */
@ExtendWith(MockitoExtension.class)
class XThreadPublishTriggerSchedulerTest {

    private static final Instant SINCE = Instant.parse("2026-08-02T08:43:52Z");

    @Mock
    private MarketingHoldingCommitService holdingCommitService;

    @Mock
    private AsmProperties asmProperties;

    @InjectMocks
    private XThreadPublishTriggerScheduler scheduler;

    @BeforeEach
    void enableTrigger() {
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", true);
    }

    @Test
    void pollAndPublishToXThread_triggerDisabled_doesNotCommit() {
        ReflectionTestUtils.setField(scheduler, "triggerEnabled", false);

        scheduler.pollAndPublishToXThread();

        verify(holdingCommitService, never()).runCommitTick(any(Instant.class));
    }

    @Test
    void pollAndPublishToXThread_asmDisabled_doesNotCommit() {
        when(asmProperties.isEnabled()).thenReturn(false);

        scheduler.pollAndPublishToXThread();

        verify(holdingCommitService, never()).runCommitTick(any(Instant.class));
    }

    @Test
    void pollAndPublishToXThread_sinceUnset_failClosed_doesNotCommit() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(null);

        scheduler.pollAndPublishToXThread();

        verify(holdingCommitService, never()).runCommitTick(any(Instant.class));
    }

    @Test
    void pollAndPublishToXThread_sinceInvalid_failClosed_doesNotCommit() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn("not-an-instant");

        scheduler.pollAndPublishToXThread();

        verify(holdingCommitService, never()).runCommitTick(any(Instant.class));
    }

    @Test
    void pollAndPublish_enabled_delegatesToCommitService() {
        when(asmProperties.isEnabled()).thenReturn(true);
        when(asmProperties.getAutoPublishSince()).thenReturn(SINCE.toString());

        scheduler.pollAndPublishToXThread();

        verify(holdingCommitService).runCommitTick(SINCE);
    }
}
