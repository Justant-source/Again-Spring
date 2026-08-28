package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingPublicationStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * (job, platform)별 "지표가 있는" 최신 스냅샷 선택.
 *
 * 배경(2026-08-29): X 스크래핑이 로그인 벽·레이아웃 변경으로 빈 metrics를 돌려주거나 어드민
 * 수동 수집이 skip_slow로 X를 건너뛰면 지표가 전부 null인 행이 더 나중 collected_at으로 쌓인다.
 * 단순 MAX(collected_at) 선택은 이 빈 행을 골라 직전 정상 수집분(노출 200~460)을 0으로
 * 보이게 했다 — prod 기준 X 24개 잡 중 20개가 이렇게 가려져 있었다.
 */
public final class MarketingStatsSnapshots {

    private MarketingStatsSnapshots() {
    }

    public static List<MarketingPublicationStats> latestWithMetricsPerJobPlatform(
        List<MarketingPublicationStats> rows
    ) {
        Map<String, MarketingPublicationStats> latest = new HashMap<>();
        for (MarketingPublicationStats row : rows) {
            if (row == null || row.getJobId() == null || row.getCollectedAt() == null) {
                continue;
            }
            if (!row.hasAnyMetric()) {
                continue;
            }
            String key = row.getJobId() + "|" + MarketingPopularityScorer.normalizePlatform(row.getPlatform());
            MarketingPublicationStats prev = latest.get(key);
            if (prev == null || row.getCollectedAt().isAfter(prev.getCollectedAt())) {
                latest.put(key, row);
            }
        }
        return new ArrayList<>(latest.values());
    }
}
