'use client';

import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  listMarketingPlatforms,
  updateMarketingPlatformAuto,
  MarketingPlatformAuto,
} from '@/lib/api/admin/marketing';

const PLATFORM_LABELS: Record<string, string> = {
  x: 'X (트위터)',
  x_thread: 'X 4단 스레드',
  instagram_feed: 'Instagram 피드',
  instagram_reels: 'Instagram 릴스',
  naver_blog: '네이버 블로그',
  naver_clip: '네이버 클립',
  youtube_shorts: 'YouTube Shorts',
  threads: 'Threads',
};

const platformLabel = (p: string) => PLATFORM_LABELS[p] ?? p;

function extractError(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as {
      response?: { data?: { message?: string; detail?: string } };
      message?: string;
    };
    const data = anyErr.response?.data;
    if (data?.message) return data.message;
    if (data?.detail) return data.detail;
    if (anyErr.message) return anyErr.message;
  }
  return String(err);
}

/** Admin: per-platform auto-publish on/off. Self-contained for S6 assembler import. */
export function PlatformAutoSection() {
  const [platforms, setPlatforms] = useState<MarketingPlatformAuto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [toggling, setToggling] = useState<string | null>(null);
  /** Last warning from PUT (unsupported enable) — shown inline. */
  const [warningByPlatform, setWarningByPlatform] = useState<Record<string, string>>({});

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setPlatforms(await listMarketingPlatforms());
    } catch (err: unknown) {
      setError(`자동 게시 대상을 불러오지 못했습니다: ${extractError(err)}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleToggle = async (row: MarketingPlatformAuto) => {
    const next = !row.autoEnabled;
    setToggling(row.platform);
    setError(null);
    try {
      const updated = await updateMarketingPlatformAuto(row.platform, next);
      setPlatforms((prev) =>
        prev.map((p) => (p.platform === updated.platform ? { ...p, ...updated } : p))
      );

      if (updated.warning) {
        setWarningByPlatform((prev) => ({
          ...prev,
          [updated.platform]: updated.warning!,
        }));
        toast.warning(updated.warning);
      } else {
        setWarningByPlatform((prev) => {
          const { [updated.platform]: _, ...rest } = prev;
          return rest;
        });
      }
    } catch (err: unknown) {
      setError(`변경에 실패했습니다: ${extractError(err)}`);
    } finally {
      setToggling(null);
    }
  };

  return (
    <div data-testid="marketing-platform-auto-section">
      <div className="mb-4 flex items-center justify-between gap-4">
        <div>
          <h3 className="font-semibold text-gray-800">자동 게시 대상</h3>
          <p className="mt-1 text-sm text-gray-500">
            24h 자동 분배에 포함할 플랫폼을 켜고 끕니다. 모든 플랫폼을 자유롭게 전환할 수
            있습니다.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          {loading ? '로드 중…' : '새로고침'}
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      {loading ? (
        <div className="py-8 text-center text-gray-400">로드 중…</div>
      ) : platforms.length === 0 ? (
        <Card className="p-6 text-center text-gray-400">플랫폼이 없습니다.</Card>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {platforms.map((row) => {
            const busy = toggling === row.platform;
            const warning = warningByPlatform[row.platform] ?? row.warning ?? null;
            return (
              <Card
                key={row.platform}
                className="flex flex-col p-4"
                data-testid={`marketing-platform-auto-${row.platform}`}
              >
                <div className="mb-3 flex items-center justify-between gap-2">
                  <span className="font-medium">{platformLabel(row.platform)}</span>
                  <button
                    type="button"
                    role="switch"
                    aria-checked={row.autoEnabled}
                    aria-label={`${platformLabel(row.platform)} 자동 게시`}
                    disabled={busy}
                    onClick={() => handleToggle(row)}
                    className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${
                      row.autoEnabled ? 'bg-[#5F8F76]' : 'bg-gray-300'
                    }`}
                  >
                    <span
                      className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow transition-transform ${
                        row.autoEnabled ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>

                <div className="mt-auto text-xs text-gray-500">
                  {busy ? '저장 중…' : row.autoEnabled ? '자동 게시 ON' : '자동 게시 OFF'}
                </div>

                {warning && row.autoEnabled && (
                  <div className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                    {warning}
                  </div>
                )}
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
