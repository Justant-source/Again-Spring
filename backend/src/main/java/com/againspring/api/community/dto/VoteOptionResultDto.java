package com.againspring.api.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투표 결과 선택지 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteOptionResultDto {

    private Long id;

    private String label;

    private Long count;

    private Double percentage;
}
