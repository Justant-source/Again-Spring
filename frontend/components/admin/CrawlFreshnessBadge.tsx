'use client';

import { useEffect, useState } from 'react';
import { AlertTriangle, RefreshCw, CheckCircle } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { getCrawlStatus, type CrawlStatusResponse } from '@/lib/api/admin/crawl';
import { cn } from '@/lib/utils';

interface CrawlFreshnessBadgeProps {
  refreshSignal?: number;
}

export function CrawlFreshnessBadge({ refreshSignal = 0 }: CrawlFreshnessBadgeProps) {
  const [status, setStatus] = useState<CrawlStatusResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadStatus = async () => {
    try {
      setLoading(true);
      const data = await getCrawlStatus();
      setStatus(data);
      setError(null);
    } catch (e: any) {
      setError(e.response?.data?.errorMessage || '크롤 상태 조회 실패');
      setStatus(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStatus();
  }, [refreshSignal]);

  if (error) {
    return (
      <Card className="p-6 bg-red-50 border-red-200">
        <div className="flex items-start gap-3">
          <AlertTriangle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
          <div>
            <h3 className="text-sm font-semibold text-red-900">크롤 상태 조회 오류</h3>
            <p className="text-xs text-red-700 mt-1">{error}</p>
          </div>
        </div>
      </Card>
    );
  }

  if (loading || !status) {
    return (
      <Card className="p-6">
        <div className="flex items-center gap-3 text-gray-500">
          <RefreshCw className="w-4 h-4 animate-spin" />
          <span className="text-sm">크롤 신선도 조회 중…</span>
        </div>
      </Card>
    );
  }

  const isStale = status.stale === true;
  const savedBySource24h = status.savedBySource24h ?? {};
  const lastSuccessfulAt = status.lastSuccessfulAt ?? {};

  const checkedAtDate = new Date(status.checkedAt);
  const checkedAtStr = checkedAtDate.toLocaleString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  });

  return (
    <Card
      className={cn(
        'p-6 transition-colors',
        isStale
          ? 'bg-red-50 border-red-200'
          : 'bg-green-50 border-green-200'
      )}
      data-testid="admin-crawl-freshness-badge"
    >
      <div className="flex items-start gap-3">
        {isStale ? (
          <AlertTriangle className="w-5 h-5 text-red-600 flex-shrink-0 mt-0.5" />
        ) : (
          <CheckCircle className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
        )}
        <div className="flex-1">
          <div className="flex items-center justify-between">
            <h3 className={cn(
              'text-sm font-semibold',
              isStale ? 'text-red-900' : 'text-green-900'
            )}>
              자동화 파이프라인 — 크롤링 신선도
            </h3>
            <span className="text-xs text-gray-500">
              조회: {checkedAtStr}
            </span>
          </div>

          <div className="mt-3 space-y-2">
            {isStale ? (
              <p className="text-sm text-red-700 font-medium">
                ⚠️ 지난 24시간 크롤링 성공 기록 없음
              </p>
            ) : (
              <p className="text-sm text-green-700 font-medium">
                ✓ 24시간 이내에 크롤링 데이터 저장됨
              </p>
            )}

            {/* 소스별 현황 */}
            <div className="mt-3 grid grid-cols-2 sm:grid-cols-3 gap-3 pt-3 border-t border-gray-200">
              {Object.entries(savedBySource24h).map(([source, count]) => {
                const lastAt = lastSuccessfulAt[source];
                const lastAtDate = lastAt ? new Date(lastAt) : null;
                const lastAtStr = lastAtDate ? lastAtDate.toLocaleString('ko-KR', {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                  hour12: false,
                }) : '기록 없음';

                return (
                  <div key={source} className="text-xs">
                    <div className="font-semibold text-gray-900 capitalize">
                      {source}
                    </div>
                    <div className="text-gray-600 mt-0.5">
                      24h: {count}건
                    </div>
                    <div className={cn(
                      'text-xs mt-1 truncate',
                      lastAt ? 'text-gray-500' : 'text-red-600 font-medium'
                    )}>
                      {lastAtStr}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* 실패 정보 */}
            {status.failureCount24h > 0 && (
              <div className="mt-2 text-xs text-gray-600">
                최근 24h 실패: {status.failureCount24h}건
              </div>
            )}
          </div>
        </div>
      </div>
    </Card>
  );
}
