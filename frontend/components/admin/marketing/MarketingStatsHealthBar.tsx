'use client';

import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  MARKETING_STATS_PLATFORM_LABELS,
  type MarketingStatsHealth,
  type MarketingStatsPlatform,
  type MarketingStatsCollectSummary,
  collectMarketingPlatformStats,
} from '@/lib/api/admin/marketing';

interface MarketingStatsHealthBarProps {
  health: MarketingStatsHealth | null;
  collecting?: boolean;
  collectElapsed?: number;
  onCollectStart?: () => void;
  onCollectTick?: (elapsedSec: number) => void;
  onCollectDone?: (summary: MarketingStatsCollectSummary) => void;
  onCollectError?: (message: string) => void;
  /** Controlled collect — parent owns state when provided. */
  onCollect?: () => void | Promise<void>;
}

function statusChipClass(status: string): string {
  switch (status) {
    case 'ok':
    case 'healthy':
      return 'border-green-200 bg-green-50 text-green-800';
    case 'partial':
      return 'border-amber-200 bg-amber-50 text-amber-800';
    case 'error':
    case 'failed':
      return 'border-red-200 bg-red-50 text-red-800';
    default:
      return 'border-gray-200 bg-gray-50 text-gray-600';
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'ok':
    case 'healthy':
      return '정상';
    case 'partial':
      return '부분';
    case 'error':
    case 'failed':
      return '오류';
    default:
      return '미확인';
  }
}

function formatCollectAt(iso: string | null): string {
  if (!iso) return '기록 없음';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function platformLabel(platform: string): string {
  return (
    MARKETING_STATS_PLATFORM_LABELS[platform as MarketingStatsPlatform] ?? platform
  );
}

export function MarketingStatsHealthBar({
  health,
  collecting = false,
  collectElapsed = 0,
  onCollectStart,
  onCollectTick,
  onCollectDone,
  onCollectError,
  onCollect,
}: MarketingStatsHealthBarProps) {
  const handleCollect = async () => {
    if (onCollect) {
      await onCollect();
      return;
    }
    onCollectStart?.();
    try {
      const summary = await collectMarketingPlatformStats({
        lookbackDays: 14,
        limit: 40,
        onTick: (sec) => onCollectTick?.(sec),
      });
      onCollectDone?.(summary);
    } catch (err: unknown) {
      onCollectError?.(err instanceof Error ? err.message : String(err));
    }
  };

  const channels = health?.channels ?? [];

  return (
    <Card className="p-4" data-testid="marketing-stats-health">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-2 min-w-0">
          <h3 className="text-sm font-semibold text-gray-800">데이터 건강</h3>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-gray-600">
            <span>
              마지막 수집{' '}
              <span className="font-medium text-gray-800">
                {formatCollectAt(health?.lastCollectAt ?? null)}
              </span>
            </span>
            <span>
              partial{' '}
              <span className="font-mono font-medium text-gray-800">
                {health?.partialCount ?? 0}
              </span>
            </span>
            <span>
              errors{' '}
              <span className="font-mono font-medium text-gray-800">
                {health?.errorCount ?? 0}
              </span>
            </span>
          </div>
          <div className="flex flex-wrap gap-1.5 pt-1">
            {channels.length === 0 && (
              <span className="text-xs text-gray-400">채널 상태 없음</span>
            )}
            {channels.map((ch) => (
              <span
                key={ch.platform}
                title={ch.message || undefined}
                className={`inline-flex items-center gap-1 rounded-md border px-2 py-0.5 text-xs ${statusChipClass(ch.status)}`}
              >
                <span className="font-medium">{platformLabel(ch.platform)}</span>
                <span className="opacity-80">{statusLabel(ch.status)}</span>
              </span>
            ))}
          </div>
        </div>
        <Button
          onClick={() => void handleCollect()}
          disabled={collecting}
          data-testid="marketing-stats-collect"
        >
          {collecting ? `수집 중… ${collectElapsed}s` : '통계 수집'}
        </Button>
      </div>
    </Card>
  );
}
