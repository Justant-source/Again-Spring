package com.againspring.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceRequest {

    @PositiveOrZero
    @Min(0)
    private Long impressions;

    @PositiveOrZero
    @Min(0)
    private Long likes;

    @PositiveOrZero
    @Min(0)
    private Long comments;

    @PositiveOrZero
    @Min(0)
    private Long shares;

    @PositiveOrZero
    @Min(0)
    private Long clicks;

    @PositiveOrZero
    @Min(0)
    private Long saves;

    private String note;
}
