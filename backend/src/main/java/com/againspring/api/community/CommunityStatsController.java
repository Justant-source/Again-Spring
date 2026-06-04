package com.againspring.api.community;

import com.againspring.service.DailyStatsAggregatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 커뮤니티 공개 통계 API
 */
@RestController
@RequestMapping("/api/community/stats")
@RequiredArgsConstructor
@Tag(name = "Community", description = "커뮤니티 공개 통계")
public class CommunityStatsController {

    private final DailyStatsAggregatorService aggregatorService;

    /**
     * 오늘(KST) 투표 수 실시간 조회 — 공개 엔드포인트
     */
    @GetMapping("/today")
    @Operation(summary = "오늘 투표 수 조회")
    public ResponseEntity<Map<String, Long>> getTodayVoteCount() {
        long count = aggregatorService.countTodayVotes();
        return ResponseEntity.ok(Map.of("voteCount", count));
    }
}
