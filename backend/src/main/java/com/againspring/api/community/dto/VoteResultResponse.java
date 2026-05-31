package com.againspring.api.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 투표 결과 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteResultResponse {

    private List<VoteOptionResultDto> options;

    private Long totalVotes;

    private Long myVotedOptionId;
}
