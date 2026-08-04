package com.againspring.aiuser.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PairedPhase2Response {
    String provider;
    String model;
    String correlationId;
    /** Workload id for orchestrator logging / provider snapshots. */
    String workload;
    /** Null when {@code includePartnerPost=false} (comment-only micro-batch). */
    @JsonProperty("partner_post")
    PartnerPost partnerPost;
    List<ThreadPlanResponse.Item> items;
    long elapsedMs;

    @Value
    @Builder
    public static class PartnerPost {
        String body;
        @JsonProperty("capture_split_after_lines")
        List<Integer> captureSplitAfterLines;
    }
}
