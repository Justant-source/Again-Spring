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
 * 시뮬레이션 상세 응답 DTO.
 * V15.3: 시뮬레이션의 전체 세부 정보 반환.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("storyId")
    private Long storyId;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("personaA")
    private String personaA;

    @JsonProperty("personaB")
    private String personaB;

    @JsonProperty("turnCount")
    private Integer turnCount;

    @JsonProperty("actualTurnCount")
    private Integer actualTurnCount;

    @JsonProperty("status")
    private String status;

    @JsonProperty("conversationLog")
    private String conversationLog;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("llmCostUsd")
    private String llmCostUsd;

    @JsonProperty("startedAt")
    private Instant startedAt;

    @JsonProperty("finishedAt")
    private Instant finishedAt;

    @JsonProperty("createdAt")
    private Instant createdAt;

    /**
     * MarketingSimulation 엔티티로부터 응답 DTO 생성.
     */
    public static SimulationResponse from(MarketingSimulation simulation) {
        return SimulationResponse.builder()
            .id(simulation.getId())
            .storyId(simulation.getSourceStoryId())
            .sessionId(simulation.getSessionId())
            .personaA(simulation.getPersonaA())
            .personaB(simulation.getPersonaB())
            .turnCount(simulation.getTurnCount())
            .actualTurnCount(simulation.getActualTurnCount())
            .status(simulation.getStatus() != null ? simulation.getStatus().toString() : null)
            .conversationLog(simulation.getConversationLog())
            .errorMessage(simulation.getErrorMessage())
            .llmCostUsd(simulation.getLlmCostUsd() != null ? simulation.getLlmCostUsd().toPlainString() : null)
            .startedAt(simulation.getStartedAt())
            .finishedAt(simulation.getFinishedAt())
            .createdAt(simulation.getCreatedAt())
            .build();
    }
}
