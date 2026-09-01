package com.againspring.marketing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class XGrowthLoopSchedulerTest {

    @Mock
    private XRitualPublisher ritualPublisher;
    @Mock
    private XInboundService inboundService;
    @Mock
    private XOutboundService outboundService;
    @Mock
    private XOriginalPostService originalPostService;

    @InjectMocks
    private XGrowthLoopScheduler scheduler;

    @BeforeEach
    void noOp() {
        // mocks default to do nothing
    }

    @Test
    void tick_runsRitualAndInbound_notOutbound() {
        scheduler.tick();

        verify(ritualPublisher).runIfDue(any(Instant.class));
        verify(inboundService).run(any(Instant.class));
        verify(originalPostService).runIfDue(any(Instant.class));
        verify(outboundService, never()).run(any(Instant.class));
    }

    @Test
    void tick_ritualFailure_stillRunsInbound_skipsOutbound() {
        doThrow(new RuntimeException("ritual boom")).when(ritualPublisher).runIfDue(any());

        scheduler.tick();

        verify(inboundService).run(any(Instant.class));
        verify(originalPostService).runIfDue(any(Instant.class));
        verify(outboundService, never()).run(any(Instant.class));
    }

    @Test
    void tick_inboundFailure_stillRunsOriginal_skipsOutbound() {
        doThrow(new RuntimeException("inbound boom")).when(inboundService).run(any());

        scheduler.tick();

        verify(ritualPublisher).runIfDue(any(Instant.class));
        verify(originalPostService).runIfDue(any(Instant.class));
        verify(outboundService, never()).run(any(Instant.class));
    }

    @Test
    void tick_originalFailure_isIsolated() {
        doThrow(new RuntimeException("original boom")).when(originalPostService).runIfDue(any());

        scheduler.tick();

        verify(ritualPublisher).runIfDue(any(Instant.class));
        verify(inboundService).run(any(Instant.class));
        verify(outboundService, never()).run(any(Instant.class));
    }

    @Test
    void outboundTick_runsOutboundOnly() {
        scheduler.outboundTick();

        verify(outboundService).run(any(Instant.class));
        verify(ritualPublisher, never()).runIfDue(any());
        verify(inboundService, never()).run(any());
        verify(originalPostService, never()).runIfDue(any());
    }

    @Test
    void outboundTick_failure_isIsolated() {
        doThrow(new RuntimeException("outbound boom")).when(outboundService).run(any());

        scheduler.outboundTick();

        verify(outboundService).run(any(Instant.class));
    }
}
