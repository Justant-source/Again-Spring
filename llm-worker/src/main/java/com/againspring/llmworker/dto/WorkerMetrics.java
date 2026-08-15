package com.againspring.llmworker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkerMetrics {
    private final int poolSize;
    private final int active;
    private final int queued;
    private final int available;
    private final long completed;
    private final long rejected;
    private final long throttled;
    private final long timedOut;
    private final long manuallyCanceled;
    private final long terminatedProcesses;
    private final long forcedTerminations;
    private final int activeProcessCount;
}
