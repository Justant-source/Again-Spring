package com.againspring.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 크롤 신선도 조회 응답 DTO.
 * 프론트엔드가 배지를 그리기 쉽게 서버에서 24시간 통계를 계산해 제공.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CrawlStatusResponse {
    /**
     * 각 크롤 소스별 최근 24시간 저장 건수 합계.
     * e.g. {"natepan": 120, "blind": 45, "theqoo": 0}
     */
    private Map<String, Integer> savedBySource24h;

    /**
     * 각 소스의 마지막 성공 크롤 시각 (ISO-8601 UTC).
     * e.g. {"natepan": "2026-07-29T14:30:00Z", "blind": "2026-07-28T10:15:00Z"}
     * 값이 없으면 (성공 기록 없음) 해당 소스는 키가 없을 수 있음.
     */
    private Map<String, String> lastSuccessfulAt;

    /**
     * 최근 24시간 내 실패 크롤 건수 합계.
     */
    private Integer failureCount24h;

    /**
     * 최근 24시간 내 성공 크롤이 0건이면 true (배지 "stale" 표시용).
     */
    private Boolean stale;

    /**
     * 조회 시각 (기준점) — ISO-8601 UTC.
     */
    private Instant checkedAt;

    /**
     * 조회 오류 시 메시지 (정상이면 null).
     */
    private String errorMessage;
}
