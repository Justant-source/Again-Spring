'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  MARKETING_STATS_PLATFORM_LABELS,
  MARKETING_STATS_PLATFORMS,
  MARKETING_THEME_CATEGORY_LABELS,
  MARKETING_THEME_EMOTION_LABELS,
  getMarketingStatsDashboard,
  getMarketingThemeMatrix,
  marketingHoldingThemeDeepLink,
  themeProposalSuggestedBoost,
  type MarketingStatsPlatform,
  type MarketingStatsUnknownCounts,
  type MarketingThemeProposal,
} from '@/lib/api/admin/marketing';

export interface MarketingStatsTodoStripProps {
  /** Controlled platform; defaults to internal select. */
  platform?: string;
  onPlatformChange?: (platform: string) => void;
  /** Optional unknown counts from dashboard (avoids extra fetch). */
  unknownCounts?: MarketingStatsUnknownCounts | null;
  todoHints?: string[];
  /** Jump focus to matrix apply section. */
  onApplyFocus?: () => void;
  className?: string;
}

function formatProposalLabel(p: MarketingThemeProposal): string {
  const emo = p.emotion
    ? MARKETING_THEME_EMOTION_LABELS[p.emotion] ?? p.emotion
    : null;
  const cat = p.category
    ? MARKETING_THEME_CATEGORY_LABELS[p.category] ?? p.category
    : null;
  if (emo && cat) return `${emo} × ${cat}`;
  if (emo) return `감정 ${emo}`;
  if (cat) return `카테고리 ${cat}`;
  return '테마';
}

function formatApiError(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as {
      response?: { data?: { error?: { message?: string }; message?: string } };
      message?: string;
    };
    return (
      anyErr.response?.data?.error?.message ||
      anyErr.response?.data?.message ||
      anyErr.message ||
      String(err)
    );
  }
  return String(err);
}

/**
 * Stats-tab action strip: apply CTA summary, pin deep links, unknown warnings.
 */
export function MarketingStatsTodoStrip({
  platform: controlledPlatform,
  onPlatformChange,
  unknownCounts: unknownProp,
  todoHints: hintsProp,
  onApplyFocus,
  className,
}: MarketingStatsTodoStripProps) {
  const [internalPlatform, setInternalPlatform] =
    useState<MarketingStatsPlatform>('x_thread');
  const platform = (controlledPlatform || internalPlatform) as string;

  const [proposals, setProposals] = useState<MarketingThemeProposal[]>([]);
  const [rolled, setRolled] = useState<MarketingThemeProposal[]>([]);
  const [unknown, setUnknown] = useState<MarketingStatsUnknownCounts | null>(
    unknownProp ?? null
  );
  const [hints, setHints] = useState<string[]>(hintsProp ?? []);
  const [canApply, setCanApply] = useState(true);
  const [cooldownUntil, setCooldownUntil] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (unknownProp !== undefined) setUnknown(unknownProp ?? null);
  }, [unknownProp]);

  useEffect(() => {
    if (hintsProp !== undefined) setHints(hintsProp ?? []);
  }, [hintsProp]);

  const setPlatform = (next: string) => {
    if (!controlledPlatform) {
      setInternalPlatform(next as MarketingStatsPlatform);
    }
    onPlatformChange?.(next);
  };

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const matrixPromise = getMarketingThemeMatrix({ platform });
      const dashPromise =
        unknownProp === undefined || hintsProp === undefined
          ? getMarketingStatsDashboard({ platform })
          : Promise.resolve(null);

      const [matrix, dash] = await Promise.all([matrixPromise, dashPromise]);
      setProposals(matrix.proposals ?? []);
      setRolled(matrix.rolledProposals ?? []);
      setCanApply(matrix.canApply !== false);
      setCooldownUntil(matrix.cooldownUntil ?? null);

      if (dash) {
        if (unknownProp === undefined) setUnknown(dash.unknownCounts);
        if (hintsProp === undefined) setHints(dash.todoHints ?? []);
      }
    } catch (err: unknown) {
      setError(formatApiError(err));
    } finally {
      setLoading(false);
    }
  }, [platform, unknownProp, hintsProp]);

  useEffect(() => {
    load();
  }, [load]);

  const pinCandidates = useMemo(() => {
    const all = [...proposals, ...rolled];
    const seen = new Set<string>();
    const out: MarketingThemeProposal[] = [];
    for (const p of all) {
      const key = `${p.emotion ?? ''}|${p.category ?? ''}`;
      if (seen.has(key)) continue;
      seen.add(key);
      out.push(p);
      if (out.length >= 4) break;
    }
    return out;
  }, [proposals, rolled]);

  const applySummary = useMemo(() => {
    const n = proposals.length + rolled.length;
    if (n === 0) return '확정할 테마 제안이 없습니다.';
    const sample = [...proposals, ...rolled]
      .slice(0, 2)
      .map((p) => {
        const sug = themeProposalSuggestedBoost(p);
        const cur = p.currentBoost;
        if (sug == null) return formatProposalLabel(p);
        if (cur != null) {
          return `${formatProposalLabel(p)} ${cur.toFixed(2)}→${sug.toFixed(2)}`;
        }
        return `${formatProposalLabel(p)} → ${sug.toFixed(2)}`;
      })
      .join(' · ');
    return `제안 ${n}건${sample ? ` (${sample}${n > 2 ? ' …' : ''})` : ''}`;
  }, [proposals, rolled]);

  const missingEmotion = unknown?.missingEmotion ?? 0;
  const missingCategory = unknown?.missingCategory ?? 0;
  const hasUnknown = missingEmotion > 0 || missingCategory > 0;

  return (
    <Card
      className={`p-4 space-y-3 ${className ?? ''}`}
      data-testid="marketing-stats-todo"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-800">할 일</h3>
          <p className="text-xs text-gray-500 mt-0.5">
            제안 확정 · 대기 핀 딥링크 · 미기입 경고
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Select value={platform} onValueChange={setPlatform}>
            <SelectTrigger className="w-[160px] h-8 text-xs" aria-label="플랫폼">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {MARKETING_STATS_PLATFORMS.map((p) => (
                <SelectItem key={p} value={p}>
                  {MARKETING_STATS_PLATFORM_LABELS[p]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" size="sm" onClick={load} disabled={loading}>
            새로고침
          </Button>
        </div>
      </div>

      {error && (
        <p className="text-xs text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1.5">
          {error}
        </p>
      )}

      {loading && (
        <p className="text-xs text-gray-400">할 일 불러오는 중…</p>
      )}

      {!loading && (
        <div className="flex flex-wrap gap-2">
          {/* Apply CTA */}
          <div className="flex-1 min-w-[220px] rounded-md border border-gray-200 bg-gray-50 px-3 py-2">
            <p className="text-[11px] font-medium text-gray-600 uppercase tracking-wide">
              배수 확정
            </p>
            <p className="text-sm text-gray-800 mt-0.5">{applySummary}</p>
            {!canApply && cooldownUntil && (
              <p className="text-[11px] text-amber-700 mt-1">
                쿨다운 중 · 다음 가능{' '}
                {new Date(cooldownUntil).toLocaleString('ko-KR', {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            )}
            <Button
              size="sm"
              className="mt-2"
              variant={proposals.length + rolled.length > 0 ? 'default' : 'outline'}
              disabled={proposals.length + rolled.length === 0}
              onClick={() => onApplyFocus?.()}
            >
              히트맵에서 확정 →
            </Button>
          </div>

          {/* Pin deep links */}
          <div className="flex-1 min-w-[220px] rounded-md border border-gray-200 px-3 py-2">
            <p className="text-[11px] font-medium text-gray-600 uppercase tracking-wide">
              핀 권고
            </p>
            {pinCandidates.length === 0 ? (
              <p className="text-sm text-gray-400 mt-1">권고 없음</p>
            ) : (
              <ul className="mt-1 space-y-1">
                {pinCandidates.map((p, i) => (
                  <li key={`${p.emotion}-${p.category}-${i}`}>
                    <Link
                      href={marketingHoldingThemeDeepLink(p.emotion, p.category)}
                      className="text-sm text-[#C9785A] hover:underline"
                    >
                      {formatProposalLabel(p)}
                      {p.reason ? ` — ${p.reason}` : ' · 대기 보드'}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Unknown warnings */}
          <div
            className={`flex-1 min-w-[180px] rounded-md border px-3 py-2 ${
              hasUnknown
                ? 'border-amber-300 bg-amber-50'
                : 'border-gray-200 bg-white'
            }`}
          >
            <p className="text-[11px] font-medium text-gray-600 uppercase tracking-wide">
              미기입
            </p>
            {hasUnknown ? (
              <p className="text-sm text-amber-900 mt-1">
                감정 없음 {missingEmotion} · 카테고리 없음 {missingCategory}
                <span className="block text-xs text-amber-800 mt-0.5">
                  배수는 1.0 고정 · 히트맵 참고만
                </span>
              </p>
            ) : (
              <p className="text-sm text-gray-400 mt-1">이상 없음</p>
            )}
            {hints.length > 0 && (
              <ul className="mt-1 space-y-0.5">
                {hints.slice(0, 3).map((h, i) => (
                  <li key={i} className="text-[11px] text-gray-500">
                    · {h}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
