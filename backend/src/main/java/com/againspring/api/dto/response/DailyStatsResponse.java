package com.againspring.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 일별 통계 응답 (V68)
 * 대시보드 추세 차트 및 통계 페이지용
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyStatsResponse {

    @Schema(description = "통계 날짜", example = "2026-06-03")
    private LocalDate statDate;

    @Schema(description = "일간 활성 사용자 수", example = "234")
    private int dau;

    @Schema(description = "신규 사용자 수", example = "12")
    private int newUsers;

    @Schema(description = "투표 수", example = "567")
    private int voteCount;

    @Schema(description = "게시글 수", example = "23")
    private int postCount;

    @Schema(description = "의견함 수", example = "5")
    private int feedbackCount;
}
