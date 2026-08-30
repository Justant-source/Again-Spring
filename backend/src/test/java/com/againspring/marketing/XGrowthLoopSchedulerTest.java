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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class XGrowthLoopSchedulerTest {

    @Mock
    private XRitualPublisher ritualPublisher;
    @Mock
    private XInboundService inboundService;
    @Mock
    private XOutboundService outboundService;

    @InjectMocks
    private XGrowthLoopScheduler scheduler;

    @BeforeEach
    void noOp() {
        // mocks default to do nothing
    }

    @Test
    void tick_runsRitualThenInboundThenOutbound() {
        scheduler.tick();

        verify(ritualPublisher).runIfDue(any(Instant.class));
        verify(inboundService).run(any(Instant.class));
        verify(outboundService).run(any(Instant.class));
    }

    @Test
    void tick_ritualFailure_stillRunsInboundAndOutbound() {
        doThrow(new RuntimeException("ritual boom")).when(ritualPublisher).runIfDue(any());

        scheduler.tick();

        verify(inboundService).run(any(Instant.class));
        verify(outboundService).run(any(Instant.class));
    }

    @Test
    void tick_inboundFailure_stillRunsOutbound() {
        doThrow(new RuntimeException("inbound boom")).when(inboundService).run(any());

        scheduler.tick();

        verify(ritualPublisher).runIfDue(any(Instant.class));
        verify(outboundService).run(any(Instant.class));
    }
}
