'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { AcquisitionFunnelPanel } from '@/components/admin/marketing/AcquisitionFunnelPanel';
import { MarketingStatsHealthBar } from '@/components/admin/marketing/MarketingStatsHealthBar';
import { MarketingStatsKpiPanel } from '@/components/admin/marketing/MarketingStatsKpiPanel';
import { MarketingStatsTodoStrip } from '@/components/admin/marketing/MarketingStatsTodoStrip';
import { MarketingThemeMatrixPanel } from '@/components/admin/marketing/MarketingThemeMatrixPanel';
import { MarketingStatsTimeline } from '@/components/admin/marketing/MarketingStatsTimeline';
import { MarketingWeeklyReportSection } from '@/components/admin/marketing/MarketingWeeklyReportSection';
import {
  collectMarketingPlatformStats,
  getMarketingStatsDashboard,
  type MarketingStatsDashboard,
  type MarketingStatsCollectSummary,
} from '@/lib/api/admin/marketing';

/**
 * Marketing 「통계」 tab shell (Sprint 3.1 + 3.2 matrix/timeline).
 * Order: TodoStrip → health/KPI → Matrix → Timeline → weekly report.
 */
export function MarketingStatsTab() {
  const [dashboard, setDashboard] = useState<MarketingStatsDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [collectMsg, setCollectMsg] = useState<string | null>(null);

  const [weeksAgo, setWeeksAgo] = useState(0);
  const [rangeDays, setRangeDays] = useState(7);
  const [primaryMetric, setPrimaryMetric] = useState('');
  const [themePlatform, setThemePlatform] = useState('x_thread');
  const [timelineRefresh, setTimelineRefresh] = useState(0);

  const [collecting, setCollecting] = useState(false);
  const [collectElapsed, setCollectElapsed] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getMarketingStatsDashboard({
        weeksAgo,
        rangeDays,
        primaryMetric: primaryMetric || undefined,
      });
      setDashboard(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
      setDashboard(null);
    } finally {
      setLoading(false);
    }
  }, [weeksAgo, rangeDays, primaryMetric]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleCollect = async () => {
    setCollecting(true);
    setCollectElapsed(0);
    setCollectMsg(null);
    setError(null);
    try {
      const summary: MarketingStatsCollectSummary = await collectMarketingPlatformStats({
        lookbackDays: 14,
        limit: 40,
        onTick: (sec) => setCollectElapsed(sec),
      });
      setCollectMsg(
        `수집 완료: stored ${summary.stored} · partial ${summary.partial} · errors ${summary.errors}`
      );
      await load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setCollecting(false);
      setCollectElapsed(0);
    }
  };

  return (
    <div className="space-y-6" data-testid="marketing-stats-tab">
      <p className="text-sm text-gray-500">
        4채널(X·인스타 피드/릴스·YouTube Shorts) 성과와 수집 상태입니다. 테마
        배수·히트맵은 아래 섹션에서 이어집니다.
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <span className="text-xs text-gray-500">주차</span>
          <Select
            value={String(weeksAgo)}
            onValueChange={(v) => setWeeksAgo(Number(v))}
          >
            <SelectTrigger className="h-9 w-36 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="0">이번 주</SelectItem>
              <SelectItem value="1">지난 주</SelectItem>
              <SelectItem value="2">2주 전</SelectItem>
              <SelectItem value="3">3주 전</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-1">
          <span className="text-xs text-gray-500">기간</span>
          <Select
            value={String(rangeDays)}
            onValueChange={(v) => setRangeDays(Number(v))}
          >
            <SelectTrigger className="h-9 w-28 text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="7">7일</SelectItem>
              <SelectItem value="14">14일</SelectItem>
              <SelectItem value="28">28일</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <Button
          variant="outline"
          className="h-9"
          onClick={() => void load()}
          disabled={loading || collecting}
        >
          새로고침
        </Button>
      </div>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}
      {collectMsg && (
        <div className="rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
          {collectMsg}
        </div>
      )}

      {/* 1. Todo strip */}
      <MarketingStatsTodoStrip
        platform={themePlatform}
        onPlatformChange={setThemePlatform}
        unknownCounts={dashboard?.unknownCounts ?? null}
        todoHints={dashboard?.todoHints ?? []}
        onApplyFocus={() => {
          if (typeof document !== 'undefined') {
            document
              .getElementById('marketing-theme-matrix')
              ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
          }
        }}
      />

      <MarketingStatsHealthBar
        health={dashboard?.health ?? null}
        collecting={collecting}
        collectElapsed={collectElapsed}
        onCollect={handleCollect}
      />

      <MarketingStatsKpiPanel
        dashboard={dashboard}
        loading={loading}
        primaryMetric={primaryMetric}
        onPrimaryMetricChange={setPrimaryMetric}
      />

      {/* 2.5. 유입 퍼널 — 발행 다음 칸(방문→가입). 배경: 2026-08-29 */}
      <AcquisitionFunnelPanel />

      {/* 5. Theme matrix + apply */}
      <MarketingThemeMatrixPanel
        platform={themePlatform}
        onPlatformChange={setThemePlatform}
        weeksAgo={weeksAgo}
        onApplied={() => setTimelineRefresh((n) => n + 1)}
      />

      {/* 7. Events timeline */}
      <MarketingStatsTimeline refreshKey={timelineRefresh} />

      <div className="border-t pt-6">
        <MarketingWeeklyReportSection hideCollect />
      </div>
    </div>
  );
}
