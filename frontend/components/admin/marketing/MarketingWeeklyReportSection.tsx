'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  collectMarketingPlatformStats,
  getMarketingWeeklyReport,
  type MarketingWeeklyReport,
  type MarketingStatsCollectSummary,
} from '@/lib/api/admin/marketing';

/** Minimal Phase 2.7 weekly report + manual collect hook. */
export function MarketingWeeklyReportSection() {
  const [report, setReport] = useState<MarketingWeeklyReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [collecting, setCollecting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [collectMsg, setCollectMsg] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setReport(await getMarketingWeeklyReport(0));
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleCollect = async () => {
    setCollecting(true);
    setCollectMsg(null);
    setError(null);
    try {
      const summary: MarketingStatsCollectSummary = await collectMarketingPlatformStats({
        lookbackDays: 14,
        limit: 20,
      });
      setCollectMsg(
        `수집 완료: stored ${summary.stored} · partial ${summary.partial} · errors ${summary.errors}`
      );
      await load();
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setCollecting(false);
    }
  };

  return (
    <Card className="p-6 space-y-4" data-testid="marketing-weekly-report">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-gray-800">주간 마케팅 리포트</h3>
          <p className="mt-1 text-sm text-gray-500">
            플랫폼 통계 스냅샷 + UTM 유입. 수집은 ASM best-effort(권한/세션 부족 시 partial).
          </p>
          {report && (
            <p className="mt-1 text-xs text-gray-400">
              {report.weekStart} ~ {report.weekEnd} · 사연 {report.storyCount} · 스냅샷{' '}
              {report.snapshotRows}
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={load} disabled={loading || collecting}>
            새로고침
          </Button>
          <Button onClick={handleCollect} disabled={collecting} data-testid="marketing-stats-collect">
            {collecting ? '수집 중…' : '통계 수집'}
          </Button>
        </div>
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

      {loading && !report ? (
        <div className="py-6 text-center text-gray-400">로드 중…</div>
      ) : report ? (
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <h4 className="text-sm font-medium text-gray-700 mb-2">상위 사연</h4>
            <ul className="text-sm space-y-1 max-h-48 overflow-auto">
              {report.topStories.length === 0 && (
                <li className="text-gray-400">데이터 없음</li>
              )}
              {report.topStories.map((s) => (
                <li key={s.postId} className="truncate text-gray-700">
                  <span className="text-gray-400 mr-1">{s.score.toFixed(1)}</span>
                  {s.title || s.postId}
                  {s.utmVisits > 0 && (
                    <span className="ml-1 text-xs text-gray-400">utm {s.utmVisits}</span>
                  )}
                </li>
              ))}
            </ul>
          </div>
          <div>
            <h4 className="text-sm font-medium text-gray-700 mb-2">감정별</h4>
            <ul className="text-sm space-y-1">
              {report.byEmotion.length === 0 && (
                <li className="text-gray-400">데이터 없음</li>
              )}
              {report.byEmotion.map((e) => (
                <li key={e.emotion} className="flex justify-between gap-2 text-gray-700">
                  <span>{e.emotion}</span>
                  <span className="text-gray-400">
                    {e.stories}편 · 조회 {e.views}
                  </span>
                </li>
              ))}
            </ul>
            <h4 className="text-sm font-medium text-gray-700 mb-2 mt-4">UTM 유입</h4>
            <p className="text-sm text-gray-700">
              방문 {report.utmInflow.visits} · 세션 {report.utmInflow.uniqueSessions}
            </p>
            <ul className="text-xs text-gray-500 mt-1 space-y-0.5">
              {report.utmInflow.bySource.map((s) => (
                <li key={String(s.source)}>
                  {String(s.source)}: {s.visits}
                </li>
              ))}
            </ul>
          </div>
        </div>
      ) : null}
    </Card>
  );
}
