'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
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
  applyMarketingThemeMatrix,
  getMarketingThemeMatrix,
  proposeMarketingThemeMatrix,
  themeProposalSuggestedBoost,
  type MarketingStatsPlatform,
  type MarketingThemeMatrix,
  type MarketingThemeProposal,
} from '@/lib/api/admin/marketing';

export interface MarketingThemeMatrixPanelProps {
  platform?: string;
  onPlatformChange?: (platform: string) => void;
  weeksAgo?: number;
  /** External refresh signal (increment to reload). */
  refreshKey?: number;
  /** Fired after a successful apply (e.g. refresh timeline). */
  onApplied?: () => void;
  className?: string;
}

function formatApiError(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as {
      response?: {
        status?: number;
        data?: { error?: { message?: string }; message?: string; detail?: string };
      };
      message?: string;
    };
    const data = anyErr.response?.data;
    return (
      data?.error?.message ||
      data?.message ||
      data?.detail ||
      anyErr.message ||
      String(err)
    );
  }
  return String(err);
}

function proposalLabel(p: MarketingThemeProposal): string {
  const emo = p.emotion
    ? MARKETING_THEME_EMOTION_LABELS[p.emotion] ?? p.emotion
    : null;
  const cat = p.category
    ? MARKETING_THEME_CATEGORY_LABELS[p.category] ?? p.category
    : null;
  if (emo && cat) return `${emo} × ${cat}`;
  if (emo) return `감정 · ${emo}`;
  if (cat) return `카테고리 · ${cat}`;
  return '—';
}

/** Heat intensity from score relative to max (sage→warm, no purple). */
function cellBg(score: number, maxScore: number, locked: boolean): string {
  if (locked) return 'bg-gray-100 text-gray-400';
  if (maxScore <= 0 || score <= 0) return 'bg-white text-gray-700';
  const t = Math.min(1, score / maxScore);
  if (t < 0.33) return 'bg-[#F3EDE6] text-gray-800';
  if (t < 0.66) return 'bg-[#E4D2C3] text-gray-900';
  return 'bg-[#C9785A]/20 text-gray-900';
}

/**
 * Emotion×category heatmap with proposals, diff preview, and apply.
 */
export function MarketingThemeMatrixPanel({
  platform: controlledPlatform,
  onPlatformChange,
  weeksAgo = 0,
  refreshKey = 0,
  onApplied,
  className,
}: MarketingThemeMatrixPanelProps) {
  const [internalPlatform, setInternalPlatform] =
    useState<MarketingStatsPlatform>('x_thread');
  const platform = (controlledPlatform || internalPlatform) as string;

  const [matrix, setMatrix] = useState<MarketingThemeMatrix | null>(null);
  const [loading, setLoading] = useState(true);
  const [proposing, setProposing] = useState(false);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());

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
      const data = await getMarketingThemeMatrix({ platform, weeksAgo });
      setMatrix(data);
      const stable = new Set<string>();
      [...(data.proposals ?? []), ...(data.rolledProposals ?? [])].forEach((p) => {
        stable.add(`${p.emotion ?? ''}|${p.category ?? ''}`);
      });
      setSelected(stable);
    } catch (err: unknown) {
      setError(formatApiError(err));
      setMatrix(null);
    } finally {
      setLoading(false);
    }
  }, [platform, weeksAgo]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  const cellMap = useMemo(() => {
    const m = new Map<string, (typeof matrix extends null ? never : NonNullable<typeof matrix>['cells'][number])>();
    for (const c of matrix?.cells ?? []) {
      m.set(`${c.emotion}|${c.category}`, c);
    }
    return m;
  }, [matrix]);

  const maxScore = useMemo(() => {
    let max = 0;
    for (const c of matrix?.cells ?? []) {
      if (!c.locked && c.score > max) max = c.score;
    }
    return max;
  }, [matrix]);

  const allProposals = useMemo(() => {
    const list: Array<MarketingThemeProposal & { _rolled?: boolean }> = [
      ...(matrix?.proposals ?? []).map((p) => ({ ...p, _rolled: false })),
      ...(matrix?.rolledProposals ?? []).map((p) => ({ ...p, _rolled: true, rolled: true })),
    ];
    return list;
  }, [matrix]);

  const selectedChanges = useMemo(() => {
    return allProposals
      .filter((p) => selected.has(`${p.emotion ?? ''}|${p.category ?? ''}`))
      .map((p) => {
        const boost = themeProposalSuggestedBoost(p);
        return {
          emotion: p.emotion ?? null,
          category: p.category ?? null,
          boost: boost ?? 1,
          currentBoost: p.currentBoost,
          label: proposalLabel(p),
          rolled: Boolean(p._rolled || p.rolled),
        };
      })
      .filter((c) => c.emotion || c.category);
  }, [allProposals, selected]);

  const toggleProposal = (p: MarketingThemeProposal) => {
    const key = `${p.emotion ?? ''}|${p.category ?? ''}`;
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const handlePropose = async () => {
    setProposing(true);
    setError(null);
    setMessage(null);
    try {
      const result = await proposeMarketingThemeMatrix({ platform, weeksAgo });
      if (Array.isArray(result)) {
        setMatrix((prev) =>
          prev
            ? { ...prev, proposals: result, rolledProposals: prev.rolledProposals }
            : {
                platform,
                emotions: [],
                categories: [],
                cells: [],
                proposals: result,
                rolledProposals: [],
              }
        );
        setSelected(
          new Set(result.map((p) => `${p.emotion ?? ''}|${p.category ?? ''}`))
        );
      } else {
        setMatrix(result);
        setSelected(
          new Set(
            [...result.proposals, ...result.rolledProposals].map(
              (p) => `${p.emotion ?? ''}|${p.category ?? ''}`
            )
          )
        );
      }
      setMessage('제안을 다시 계산했습니다 (저장되지 않음).');
    } catch (err: unknown) {
      setError(formatApiError(err));
    } finally {
      setProposing(false);
    }
  };

  const handleApply = async () => {
    if (selectedChanges.length === 0) return;
    // Rolled axis proposals without both emotion+category cannot be applied as cells.
    const cellChanges = selectedChanges.filter((c) => c.emotion && c.category);
    if (cellChanges.length === 0) {
      setError('교차 칸 제안만 적용할 수 있습니다 (말린 축은 참고용).');
      return;
    }
    setApplying(true);
    setError(null);
    setMessage(null);
    try {
      const result = await applyMarketingThemeMatrix({
        platform,
        changes: cellChanges.map((c) => ({
          emotion: c.emotion,
          category: c.category,
          boost: c.boost,
        })),
        confirm: true,
      });
      setMessage(
        `적용 완료 ${result.applied}칸` +
          (result.cooldownUntil
            ? ` · 다음 확정 ${new Date(result.cooldownUntil).toLocaleString('ko-KR')}`
            : '')
      );
      onApplied?.();
      await load();
    } catch (err: unknown) {
      setError(formatApiError(err));
    } finally {
      setApplying(false);
    }
  };

  const emotions = matrix?.emotions?.length
    ? matrix.emotions
    : ['shock', 'anger', 'tension', 'sad', 'hype'];
  const categories = matrix?.categories?.length
    ? matrix.categories
    : ['COUPLE', 'MARRIED', 'FRIEND', 'FAMILY', 'WORK', 'OTHER'];

  return (
    <Card
      className={`p-4 space-y-4 ${className ?? ''}`}
      data-testid="marketing-theme-matrix"
      id="marketing-theme-matrix"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-800">
            감정 × 카테고리 테마 배수
          </h3>
          <p className="text-xs text-gray-500 mt-0.5">
            N&lt;3 잠금 · 제안 후 확정 · 칸당 Δ±0.05 · 주 1회
            {matrix?.shadow !== false ? ' · shadow(배분 미반영)' : ''}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
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
          <Button
            variant="outline"
            size="sm"
            onClick={handlePropose}
            disabled={proposing || loading}
          >
            {proposing ? '제안 중…' : '제안 재계산'}
          </Button>
        </div>
      </div>

      {error && (
        <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1.5">
          {error}
        </p>
      )}
      {message && (
        <p className="text-xs text-emerald-800 bg-emerald-50 border border-emerald-200 rounded px-2 py-1.5">
          {message}
        </p>
      )}

      {loading && (
        <p className="text-sm text-gray-400 py-6 text-center">매트릭스 로드 중…</p>
      )}

      {!loading && (
        <>
          {/* Heatmap */}
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-xs min-w-[520px]">
              <thead>
                <tr>
                  <th className="p-1.5 text-left text-gray-500 font-medium sticky left-0 bg-white">
                    감정 \\ 카테고리
                  </th>
                  {categories.map((cat) => (
                    <th
                      key={cat}
                      className="p-1.5 text-center text-gray-600 font-medium whitespace-nowrap"
                    >
                      {MARKETING_THEME_CATEGORY_LABELS[cat] ?? cat}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {emotions.map((emo) => (
                  <tr key={emo}>
                    <td className="p-1.5 font-medium text-gray-700 sticky left-0 bg-white whitespace-nowrap">
                      {MARKETING_THEME_EMOTION_LABELS[emo] ?? emo}
                    </td>
                    {categories.map((cat) => {
                      const cell = cellMap.get(`${emo}|${cat}`);
                      const locked = cell?.locked ?? true;
                      const n = cell?.n ?? 0;
                      const boost = cell?.boost ?? 1;
                      const score = cell?.score ?? 0;
                      return (
                        <td key={cat} className="p-0.5">
                          <div
                            className={`rounded border border-gray-200 px-1.5 py-1.5 text-center ${cellBg(
                              score,
                              maxScore,
                              locked
                            )}`}
                            title={
                              locked
                                ? `잠금 (n=${n})`
                                : `n=${n} score=${score.toFixed(1)} boost=${boost.toFixed(2)}`
                            }
                          >
                            <div className="font-mono tabular-nums">
                              {locked ? '—' : boost.toFixed(2)}
                            </div>
                            <div className="text-[10px] opacity-70 mt-0.5">
                              {locked ? 'n<3' : `n=${n}`}
                            </div>
                          </div>
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            {(matrix?.cells?.length ?? 0) === 0 && (
              <p className="text-xs text-gray-400 mt-2">
                셀 데이터가 없습니다. 수집·게시 통계가 쌓이면 히트맵이 채워집니다.
              </p>
            )}
          </div>

          {/* Proposals list */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-700">제안 목록</h4>
              {allProposals.length === 0 ? (
                <p className="text-sm text-gray-400">제안 없음 · 「제안 재계산」을 눌러 보세요.</p>
              ) : (
                <ul className="space-y-1.5 max-h-56 overflow-y-auto">
                  {allProposals.map((p, i) => {
                    const key = `${p.emotion ?? ''}|${p.category ?? ''}`;
                    const checked = selected.has(key);
                    const sug = themeProposalSuggestedBoost(p);
                    return (
                      <li key={`${key}-${i}`}>
                        <label className="flex items-start gap-2 rounded border border-gray-200 px-2 py-1.5 hover:bg-gray-50 cursor-pointer">
                          <input
                            type="checkbox"
                            className="mt-1"
                            checked={checked}
                            onChange={() => toggleProposal(p)}
                          />
                          <span className="flex-1 min-w-0">
                            <span className="text-sm text-gray-800">
                              {proposalLabel(p)}
                            </span>
                            {(p._rolled || p.rolled) && (
                              <Badge className="ml-1 bg-gray-100 text-gray-600 text-[10px]">
                                말린 축
                              </Badge>
                            )}
                            <span className="block text-xs text-gray-500 font-mono mt-0.5">
                              {p.currentBoost != null ? p.currentBoost.toFixed(2) : '?'}
                              {' → '}
                              {sug != null ? sug.toFixed(2) : '?'}
                              {p.n != null ? ` · n=${p.n}` : ''}
                            </span>
                            {p.reason && (
                              <span className="block text-[11px] text-gray-500 mt-0.5">
                                {p.reason}
                              </span>
                            )}
                          </span>
                        </label>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>

            {/* Diff + Apply */}
            <div className="space-y-2">
              <h4 className="text-xs font-semibold text-gray-700">확정 diff</h4>
              {selectedChanges.length === 0 ? (
                <p className="text-sm text-gray-400">선택된 제안이 없습니다.</p>
              ) : (
                <ul className="space-y-1 text-sm max-h-40 overflow-y-auto border border-gray-200 rounded-md p-2 bg-gray-50">
                  {selectedChanges.map((c, i) => (
                    <li
                      key={`${c.emotion}-${c.category}-${i}`}
                      className="flex justify-between gap-2 font-mono text-xs"
                    >
                      <span className="text-gray-700 truncate">{c.label}</span>
                      <span className="text-gray-900 whitespace-nowrap">
                        {(c.currentBoost ?? 1).toFixed?.(2) ?? c.currentBoost}
                        {' → '}
                        {c.boost.toFixed(2)}
                        {c.rolled ? ' (참고)' : ''}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
              {matrix?.cooldownUntil && matrix.canApply === false && (
                <p className="text-[11px] text-amber-700">
                  쿨다운 ·{' '}
                  {new Date(matrix.cooldownUntil).toLocaleString('ko-KR')}
                </p>
              )}
              <Button
                onClick={handleApply}
                disabled={
                  applying ||
                  selectedChanges.filter((c) => c.emotion && c.category).length === 0 ||
                  matrix?.canApply === false
                }
                data-testid="marketing-theme-matrix-apply"
              >
                {applying ? '적용 중…' : '선택한 배수 확정'}
              </Button>
            </div>
          </div>
        </>
      )}
    </Card>
  );
}
