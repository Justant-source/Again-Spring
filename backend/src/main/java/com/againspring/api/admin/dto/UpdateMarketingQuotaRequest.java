package com.againspring.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMarketingQuotaRequest {

    @NotNull
    @Min(1)
    @Max(50)
    private Integer dailyTextCap;

    @NotNull
    @Min(0)
    @Max(50)
    private Integer dailyVideoCap;
}
