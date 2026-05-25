package com.againspring.api.dto.response;

import com.againspring.domain.marketing.MarketingSimulation;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 시뮬레이션 요약 응답 DTO.
 * V15.3: 시뮬레이션 목록 조회 시 사용 (경량).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationSummaryResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("storyId")
    private Long storyId;

    @JsonProperty("turnCount")
    private Integer turnCount;

    @JsonProperty("actualTurnCount")
    private Integer actualTurnCount;

    @JsonProperty("status")
    private String status;

    @JsonProperty("startedAt")
    private Instant startedAt;

    @JsonProperty("finishedAt")
    private Instant finishedAt;

    @JsonProperty("createdAt")
    private Instant createdAt;

    /**
     * MarketingSimulation 엔티티로부터 요약 응답 DTO 생성.
     */
    public static SimulationSummaryResponse from(MarketingSimulation simulation) {
        return SimulationSummaryResponse.builder()
            .id(simulation.getId())
            .storyId(simulation.getSourceStoryId())
            .turnCount(simulation.getTurnCount())
            .actualTurnCount(simulation.getActualTurnCount())
            .status(simulation.getStatus() != null ? simulation.getStatus().toString() : null)
            .startedAt(simulation.getStartedAt())
            .finishedAt(simulation.getFinishedAt())
            .createdAt(simulation.getCreatedAt())
            .build();
    }
}
