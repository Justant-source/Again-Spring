package com.againspring.api.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 투표 선택지 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteOptionDto {

    private Long id;

    private String label;

    private Integer orderIdx;
}
