'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  MARKETING_STATS_PLATFORM_LABELS,
  getMarketingStatsEvents,
  type MarketingStatsEventDto,
  type MarketingStatsPlatform,
} from '@/lib/api/admin/marketing';

const EVENT_LABELS: Record<string, string> = {
  COLLECT_STARTED: '수집 시작',
  COLLECT_COMPLETED: '수집 완료',
  COLLECT_FAILED: '수집 실패',
  PROPOSE: '테마 제안',
  APPLY: '배수 확정',
  SHADOW_TOGGLE: 'shadow 전환',
};

const EVENT_BADGE: Record<string, string> = {
  COLLECT_STARTED: 'bg-blue-50 text-blue-800',
  COLLECT_COMPLETED: 'bg-emerald-50 text-emerald-800',
  COLLECT_FAILED: 'bg-red-50 text-red-800',
  PROPOSE: 'bg-amber-50 text-amber-900',
  APPLY: 'bg-[#5F8F76]/15 text-[#3d6b55]',
  SHADOW_TOGGLE: 'bg-gray-100 text-gray-700',
};

export interface MarketingStatsTimelineProps {
  limit?: number;
  refreshKey?: number;
  className?: string;
}

function formatApiError(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as { message?: string };
    return anyErr.message || String(err);
  }
  return String(err);
}

function platformLabel(platform: string | null): string {
  if (!platform) return '전체';
  return (
    MARKETING_STATS_PLATFORM_LABELS[platform as MarketingStatsPlatform] ?? platform
  );
}

function summarizePayload(payloadJson: string | null): string | null {
  if (!payloadJson) return null;
  try {
    const parsed = JSON.parse(payloadJson) as Record<string, unknown>;
    const parts: string[] = [];
    if (parsed.applied != null) parts.push(`applied=${parsed.applied}`);
    if (parsed.stored != null) parts.push(`stored=${parsed.stored}`);
    if (parsed.partial != null) parts.push(`partial=${parsed.partial}`);
    if (parsed.errors != null) parts.push(`errors=${parsed.errors}`);
    if (parsed.shadow != null) parts.push(`shadow=${String(parsed.shadow)}`);
    if (parts.length > 0) return parts.join(' · ');
    const s = JSON.stringify(parsed);
    return s.length > 80 ? `${s.slice(0, 80)}…` : s;
  } catch {
    return payloadJson.length > 80 ? `${payloadJson.slice(0, 80)}…` : payloadJson;
  }
}

/**
 * Marketing stats activity timeline (collect / propose / apply / shadow).
 */
export function MarketingStatsTimeline({
  limit = 50,
  refreshKey = 0,
  className,
}: MarketingStatsTimelineProps) {
  const [events, setEvents] = useState<MarketingStatsEventDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setEvents(await getMarketingStatsEvents(limit));
    } catch (err: unknown) {
      setError(formatApiError(err));
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  return (
    <Card
      className={`p-4 space-y-3 ${className ?? ''}`}
      data-testid="marketing-stats-timeline"
    >
      <div className="flex items-center justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-gray-800">이벤트 타임라인</h3>
          <p className="text-xs text-gray-500 mt-0.5">
            수집 · 제안 · 확정 · shadow (최근 {limit}건)
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          새로고침
        </Button>
      </div>

      {error && (
        <p className="text-xs text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1.5">
          {error}
        </p>
      )}

      {loading && (
        <p className="text-sm text-gray-400 py-4 text-center">로드 중…</p>
      )}

      {!loading && events.length === 0 && (
        <p className="text-sm text-gray-400 py-4 text-center">아직 이벤트가 없습니다.</p>
      )}

      {!loading && events.length > 0 && (
        <ul className="space-y-1.5 max-h-80 overflow-y-auto">
          {events.map((ev) => {
            const badge =
              EVENT_BADGE[ev.eventType] || 'bg-gray-100 text-gray-700';
            const label = EVENT_LABELS[ev.eventType] || ev.eventType;
            const payload = summarizePayload(ev.payloadJson);
            return (
              <li
                key={ev.id || `${ev.eventType}-${ev.createdAt}`}
                className="flex items-start gap-3 rounded-md border border-gray-200 px-3 py-2 hover:bg-gray-50"
              >
                <Badge className={`${badge} shrink-0`}>{label}</Badge>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-gray-800">
                    {platformLabel(ev.platform)}
                  </p>
                  {payload && (
                    <p className="text-[11px] text-gray-500 font-mono mt-0.5 truncate">
                      {payload}
                    </p>
                  )}
                </div>
                {ev.createdAt && (
                  <span className="text-[11px] text-gray-400 whitespace-nowrap">
                    {new Date(ev.createdAt).toLocaleString('ko-KR', {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit',
                    })}
                  </span>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
