package com.againspring.api.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 배심원 투표 결과 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JuryResultResponse {

    private List<JurorDto> jurors;

    private List<DistributionDto> distribution;

    private String legalNotice;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JurorDto {
        private String ageGroup;
        private String gender;
        private String chosenOptionLabel;
        private String empathyComment;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistributionDto {
        private String label;
        private Long count;
        private Double percentage;
    }
}
