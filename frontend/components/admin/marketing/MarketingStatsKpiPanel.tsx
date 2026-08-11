'use client';

import { Card } from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  MARKETING_STATS_PLATFORM_LABELS,
  type MarketingStatsDashboard,
  type MarketingStatsPlatform,
  type MarketingStatsPlatformKpi,
} from '@/lib/api/admin/marketing';

const METRIC_OPTIONS = [
  { value: '', label: '플랫폼 기본' },
  { value: 'impressions', label: '노출(impressions)' },
  { value: 'views', label: '조회(views)' },
  { value: 'reach', label: '도달(reach)' },
  { value: 'plays', label: '재생(plays)' },
  { value: 'likes', label: '좋아요' },
  { value: 'comments', label: '댓글' },
  { value: 'saves', label: '저장' },
] as const;

const METRIC_LABELS: Record<string, string> = {
  impressions: '노출',
  views: '조회',
  reach: '도달',
  plays: '재생',
  likes: '좋아요',
  comments: '댓글',
  saves: '저장',
  replies: '답글',
  reposts: '리포스트',
  shares: '공유',
};

interface MarketingStatsKpiPanelProps {
  dashboard: MarketingStatsDashboard | null;
  loading?: boolean;
  primaryMetric?: string;
  onPrimaryMetricChange?: (metric: string) => void;
}

function platformLabel(platform: string): string {
  return (
    MARKETING_STATS_PLATFORM_LABELS[platform as MarketingStatsPlatform] ?? platform
  );
}

function metricLabel(key: string): string {
  return METRIC_LABELS[key] ?? key;
}

function formatDelta(deltaPct: number | null): { text: string; className: string } {
  if (deltaPct == null || !Number.isFinite(deltaPct)) {
    return { text: '—', className: 'text-gray-400' };
  }
  const sign = deltaPct > 0 ? '+' : '';
  const text = `${sign}${deltaPct.toFixed(1)}%`;
  if (deltaPct > 0.5) return { text, className: 'text-green-700' };
  if (deltaPct < -0.5) return { text, className: 'text-red-700' };
  return { text, className: 'text-gray-600' };
}

function formatNumber(n: number): string {
  if (!Number.isFinite(n)) return '—';
  return Math.round(n).toLocaleString('ko-KR');
}

/** Tiny sparkline from series values (no chart lib). */
function MiniSeries({ series }: { series: MarketingStatsPlatformKpi['series'] }) {
  if (!series.length) {
    return <div className="h-8 text-xs text-gray-300 flex items-end">주간 시리즈 없음</div>;
  }
  const values = series.map((s) => s.value);
  const max = Math.max(...values, 1);
  return (
    <div className="flex items-end gap-0.5 h-8" aria-hidden>
      {values.map((v, i) => (
        <div
          key={series[i]?.day ?? i}
          className="flex-1 min-w-[3px] rounded-sm bg-gray-300"
          style={{ height: `${Math.max(8, (v / max) * 100)}%` }}
          title={`${series[i]?.day ?? ''}: ${v}`}
        />
      ))}
    </div>
  );
}

export function MarketingStatsKpiPanel({
  dashboard,
  loading = false,
  primaryMetric = '',
  onPrimaryMetricChange,
}: MarketingStatsKpiPanelProps) {
  const platforms = dashboard?.platforms ?? [];
  const utm = dashboard?.utm;

  return (
    <div className="space-y-4" data-testid="marketing-stats-kpi">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-800">채널 KPI</h3>
          {dashboard?.weekStart && (
            <p className="text-xs text-gray-500 mt-0.5">
              {dashboard.weekStart} ~ {dashboard.weekEnd}
              {dashboard.prevWeekStart && (
                <>
                  {' '}
                  · 대비 {dashboard.prevWeekStart} ~ {dashboard.prevWeekEnd}
                </>
              )}
            </p>
          )}
        </div>
        {onPrimaryMetricChange && (
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">1차 지표</span>
            <Select
              value={primaryMetric || '__default__'}
              onValueChange={(v) =>
                onPrimaryMetricChange(v === '__default__' ? '' : v)
              }
            >
              <SelectTrigger className="h-9 w-44 text-sm">
                <SelectValue placeholder="플랫폼 기본" />
              </SelectTrigger>
              <SelectContent>
                {METRIC_OPTIONS.map((opt) => (
                  <SelectItem
                    key={opt.value || '__default__'}
                    value={opt.value || '__default__'}
                  >
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
      </div>

      {loading && !dashboard && (
        <div className="py-8 text-center text-sm text-gray-400">로드 중…</div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {platforms.map((row) => {
          const delta = formatDelta(row.deltaPct);
          return (
            <Card key={row.platform} className="p-4 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <h4 className="text-sm font-semibold text-gray-800">
                  {platformLabel(row.platform)}
                </h4>
                <span className="text-[11px] text-gray-400 shrink-0">
                  {metricLabel(row.primaryMetric)}
                </span>
              </div>
              <div>
                <p className="text-2xl font-semibold tabular-nums text-gray-900">
                  {formatNumber(row.value)}
                </p>
                <p className="mt-1 text-xs text-gray-500">
                  지난 주 {formatNumber(row.prevValue)}
                  <span className={`ml-2 font-medium ${delta.className}`}>
                    WoW {delta.text}
                  </span>
                </p>
              </div>
              <MiniSeries series={row.series} />
            </Card>
          );
        })}
        {!loading && platforms.length === 0 && (
          <Card className="col-span-full p-6 text-center text-gray-400 text-sm">
            KPI 데이터가 없습니다. 통계를 수집해 보세요.
          </Card>
        )}
      </div>

      <Card className="p-4 space-y-2">
        <h4 className="text-sm font-semibold text-gray-800">UTM 유입</h4>
        {!utm || (utm.visits === 0 && utm.bySource.length === 0) ? (
          <p className="text-sm text-gray-400">이번 주 UTM 유입 기록 없음</p>
        ) : (
          <>
            <p className="text-sm text-gray-700">
              방문{' '}
              <span className="font-mono font-medium">{formatNumber(utm.visits)}</span>
              {' · '}세션{' '}
              <span className="font-mono font-medium">
                {formatNumber(utm.uniqueSessions)}
              </span>
            </p>
            <ul className="text-xs text-gray-500 space-y-0.5">
              {utm.bySource.map((s) => (
                <li key={String(s.source)}>
                  {String(s.source) || '(unknown)'}: {formatNumber(s.visits)}
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>
    </div>
  );
}
