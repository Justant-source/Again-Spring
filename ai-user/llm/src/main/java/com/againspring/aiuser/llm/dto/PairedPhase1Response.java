package com.againspring.aiuser.llm.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PairedPhase1Response {
    String provider;
    String model;
    String correlationId;
    /** Workload id for orchestrator logging / provider snapshots. */
    String workload;
    ThreadPlanResponse.Post post;
    List<ThreadPlanResponse.Item> items;
    long elapsedMs;
}
