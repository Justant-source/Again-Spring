package com.againspring.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 대시보드 KPI 요약 응답
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardSummaryResponse {

    @Schema(description = "오늘 신규 회원 수", example = "12")
    private long todayNewUsers;

    @Schema(description = "총 회원 수 (삭제 제외)", example = "1234")
    private long totalUsers;

    @Schema(description = "총 게시글 수 (삭제 제외)", example = "567")
    private long totalPosts;

    @Schema(description = "총 투표 수", example = "8900")
    private long totalVotes;

    @Schema(description = "총 댓글 수", example = "3456")
    private long totalComments;

    @Schema(description = "대기 중인 신고 수", example = "5")
    private long pendingReports;

    @Schema(description = "미처리 문의 수", example = "3")
    private long openInquiries;

    @Schema(description = "오늘 의견함 수", example = "2")
    private long todayFeedback;

    @Schema(description = "오늘 투표 수", example = "234")
    private long todayVotes;
}
