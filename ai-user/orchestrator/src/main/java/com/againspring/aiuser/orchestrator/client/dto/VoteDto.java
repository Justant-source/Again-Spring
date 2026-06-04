package com.againspring.aiuser.orchestrator.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteDto {
    private Long optionId;  // VoteOption.id (BIGINT from vote_options table)
}
