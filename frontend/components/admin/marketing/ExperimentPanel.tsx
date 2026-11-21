'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  getExperiment,
  updateExperimentSettings,
  setExperimentArmEnabled,
  runExperimentScoring,
  type ExperimentAccountPoint,
  type ExperimentOverview,
} from '@/lib/api/admin/marketingExperiment';

const PLATFORM_LABELS: Record<string, string> = {
  x: 'X',
  x_thread: 'X',
  youtube: 'YouTube',
  youtube_shorts: 'Shorts',
  instagram: 'Instagram',
  instagram_feed: 'IG 피드',
  instagram_reels: '릴스',
};

const label = (p: string) => PLATFORM_LABELS[p] ?? p;
const fmt = (v: number | null | undefined, digits = 1) =>
  v == null ? '-' : v.toLocaleString('ko-KR', { maximumFractionDigits: digits });

function errMsg(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

interface AccountRow {
  platform: string;
  latest: number | null;
  latestDate: string;
  d7: number | null;
  d30: number | null;
}

/** 기준일(최신 수집일) 기준 N일 전 이하 중 가장 가까운 값 대비 증감. 값이 없으면 null. */
function buildAccountRows(points: ExperimentAccountPoint[]): AccountRow[] {
  const byPlatform = new Map<string, ExperimentAccountPoint[]>();
  for (const p of points) {
    if (p.followers == null) continue;
    const list = byPlatform.get(p.platform) ?? [];
    list.push(p);
    byPlatform.set(p.platform, list);
  }
  const rows: AccountRow[] = [];
  byPlatform.forEach((list, platform) => {
    const sorted = [...list].sort((a, b) => a.collectedOn.localeCompare(b.collectedOn));
    const last = sorted[sorted.length - 1];
    const lastMs = Date.parse(last.collectedOn);
    const diffAgo = (days: number): number | null => {
      const target = lastMs - days * 86_400_000;
      const past = [...sorted].reverse().find((p) => Date.parse(p.collectedOn) <= target);
      return past && past.followers != null && last.followers != null
        ? last.followers - past.followers
        : null;
    };
    rows.push({
      platform,
      latest: last.followers,
      latestDate: last.collectedOn,
      d7: diffAgo(7),
      d30: diffAgo(30),
    });
  });
  return rows.sort((a, b) => a.platform.localeCompare(b.platform));
}

function Delta({ v }: { v: number | null }) {
  if (v == null) return <span className="text-gray-400">-</span>;
  const cls = v > 0 ? 'text-green-700' : v < 0 ? 'text-red-600' : 'text-gray-500';
  return <span className={cls}>{v > 0 ? `+${v.toLocaleString('ko-KR')}` : v.toLocaleString('ko-KR')}</span>;
}

export function ExperimentPanel({ className }: { className?: string }) {
  const [data, setData] = useState<ExperimentOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(await getExperiment());
    } catch (err: unknown) {
      setError(`실험 현황을 불러오지 못했습니다: ${errMsg(err)}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const run = async (action: () => Promise<void>, failPrefix: string) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
      setData(await getExperiment());
    } catch (err: unknown) {
      setError(`${failPrefix}: ${errMsg(err)}`);
    } finally {
      setBusy(false);
    }
  };

  const toggleSetting = (field: 'enabled' | 'topicBiasEnabled', next: boolean, name: string) => {
    if (next && !window.confirm(`${name}을(를) 켤까요?\n실제 게시물(영상 구성·주제 선택)에 영향을 줍니다.`)) {
      return;
    }
    void run(() => updateExperimentSettings({ [field]: next }), `${name} 변경에 실패했습니다`);
  };

  const toggleArm = (key: string, next: boolean) => {
    if (next && !window.confirm(`"${key}" 포맷을 켤까요?\n켜면 새로 만들어지는 자동 게시물에 배정될 수 있습니다.`)) {
      return;
    }
    void run(() => setExperimentArmEnabled(key, next), '포맷 변경에 실패했습니다');
  };

  const scoreNow = async () => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const { created } = await runExperimentScoring();
      setNotice(created > 0 ? `${created}건을 새로 채점했습니다.` : '새로 채점할 게시물이 없습니다.');
      setData(await getExperiment());
    } catch (err: unknown) {
      setError(`채점에 실패했습니다: ${errMsg(err)}`);
    } finally {
      setBusy(false);
    }
  };

  const topics = useMemo(
    () => [...(data?.topics ?? [])].sort((a, b) => b.multiplier - a.multiplier),
    [data],
  );
  const accountRows = useMemo(() => buildAccountRows(data?.accounts ?? []), [data]);

  if (loading && !data) {
    return (
      <Card className={`p-6 ${className ?? ''}`} data-testid="marketing-experiment-panel">
        <div className="py-4 text-center text-gray-400">로드 중…</div>
      </Card>
    );
  }

  const th = 'p-1.5 text-left text-gray-500 font-medium';
  const thr = 'p-1.5 text-right text-gray-500 font-medium';

  return (
    <Card className={`p-6 space-y-6 ${className ?? ''}`} data-testid="marketing-experiment-panel">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-gray-800">포맷 실험</h3>
          <p className="mt-1 text-sm text-gray-500">
            영상 구성(포맷)을 바꿔 가며 어느 쪽이 반응이 좋은지 비교합니다. 점수는 게시 후 조회·반응을 환산한 값이고,
            &quot;확산&quot;은 조회가 평소의 3배 이상 나온 게시물입니다.
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <Button
            variant="outline"
            onClick={scoreNow}
            disabled={busy || !data}
            data-testid="experiment-score-run"
          >
            지금 채점
          </Button>
          <Button variant="outline" onClick={() => void load()} disabled={busy}>
            새로고침
          </Button>
        </div>
      </div>

      {error && (
        <div
          className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          data-testid="experiment-error"
        >
          {error}
        </div>
      )}
      {notice && (
        <div className="rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
          {notice}
        </div>
      )}

      {data && (
        <>
          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-700">스위치</h4>
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                role="switch"
                checked={data.settings.enabled}
                disabled={busy}
                onChange={(e) => toggleSetting('enabled', e.target.checked, '실험')}
                data-testid="experiment-switch-enabled"
              />
              실험 켜기 <span className="text-xs text-gray-500">— 켜진 포맷을 자동 게시물에 나눠 배정합니다</span>
            </label>
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                role="switch"
                checked={data.settings.topicBiasEnabled}
                disabled={busy}
                onChange={(e) => toggleSetting('topicBiasEnabled', e.target.checked, '주제 편향')}
                data-testid="experiment-switch-topic-bias"
              />
              주제 편향 켜기{' '}
              <span className="text-xs text-gray-500">
                — 반응이 좋았던 주제를 더 자주 고릅니다 (표본 {data.settings.minSamples}건 이상인 주제만)
              </span>
            </label>
          </div>

          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-700">포맷별 성과</h4>
            {data.arms.length === 0 ? (
              <p className="text-sm text-gray-400" data-testid="experiment-arms-empty">
                등록된 포맷이 없습니다.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-xs min-w-[640px]" data-testid="experiment-arms-table">
                  <thead>
                    <tr>
                      <th className={th}>포맷</th>
                      <th className={th}>켜기</th>
                      <th className={thr}>최근 30일 배정</th>
                      <th className={thr}>채점 수</th>
                      <th className={thr}>평균 점수</th>
                      <th className={thr}>확산</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.arms.map((a) => (
                      <tr key={a.key} className="border-t" data-testid={`experiment-arm-${a.key}`}>
                        <td className="p-1.5">
                          <div className="font-medium text-gray-800">{a.key}</div>
                          <div className="text-gray-500">{a.description}</div>
                        </td>
                        <td className="p-1.5">
                          <input
                            type="checkbox"
                            role="switch"
                            aria-label={`${a.key} 켜기`}
                            checked={a.enabled}
                            disabled={busy}
                            onChange={(e) => toggleArm(a.key, e.target.checked)}
                            data-testid={`experiment-arm-toggle-${a.key}`}
                          />
                        </td>
                        <td className="p-1.5 text-right">{fmt(a.assigned30d, 0)}</td>
                        <td className="p-1.5 text-right">{fmt(a.outcomes60d, 0)}</td>
                        <td className="p-1.5 text-right">{fmt(a.meanReward, 2)}</td>
                        <td className="p-1.5 text-right">{fmt(a.breakouts, 0)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-700">주제별 반응</h4>
            <p className="text-xs text-gray-500">배수가 1보다 크면 평균보다 반응이 좋았던 주제입니다.</p>
            {topics.length === 0 ? (
              <p className="text-sm text-gray-400" data-testid="experiment-topics-empty">
                아직 채점된 주제 데이터가 없습니다.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-xs min-w-[520px]" data-testid="experiment-topics-table">
                  <thead>
                    <tr>
                      <th className={th}>플랫폼</th>
                      <th className={th}>카테고리</th>
                      <th className={th}>감정</th>
                      <th className={thr}>표본 수</th>
                      <th className={thr}>평균 점수</th>
                      <th className={thr}>배수</th>
                    </tr>
                  </thead>
                  <tbody>
                    {topics.map((t) => (
                      <tr key={`${t.platform}|${t.category}|${t.hookEmotion}`} className="border-t">
                        <td className="p-1.5">{label(t.platform)}</td>
                        <td className="p-1.5">{t.category}</td>
                        <td className="p-1.5">{t.hookEmotion}</td>
                        <td className="p-1.5 text-right">{fmt(t.n, 0)}</td>
                        <td className="p-1.5 text-right">{fmt(t.meanReward, 2)}</td>
                        <td className="p-1.5 text-right font-medium">{fmt(t.multiplier, 2)}배</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="space-y-2">
            <h4 className="text-sm font-medium text-gray-700">계정 팔로워 추이</h4>
            {accountRows.length === 0 ? (
              <p className="text-sm text-gray-400" data-testid="experiment-accounts-empty">
                아직 수집된 팔로워 기록이 없습니다.
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full border-collapse text-xs min-w-[420px]" data-testid="experiment-accounts-table">
                  <thead>
                    <tr>
                      <th className={th}>플랫폼</th>
                      <th className={thr}>최근 값</th>
                      <th className={thr}>7일 전 대비</th>
                      <th className={thr}>30일 전 대비</th>
                    </tr>
                  </thead>
                  <tbody>
                    {accountRows.map((r) => (
                      <tr key={r.platform} className="border-t">
                        <td className="p-1.5">
                          {label(r.platform)} <span className="text-gray-400">({r.latestDate})</span>
                        </td>
                        <td className="p-1.5 text-right">{fmt(r.latest, 0)}</td>
                        <td className="p-1.5 text-right"><Delta v={r.d7} /></td>
                        <td className="p-1.5 text-right"><Delta v={r.d30} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </Card>
  );
}
