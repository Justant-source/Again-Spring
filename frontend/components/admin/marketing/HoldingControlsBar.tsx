'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  getMarketingQuota,
  updateMarketingQuota,
  getMarketingScoreWeights,
  updateMarketingScoreWeights,
  type MarketingQuota,
  type MarketingScoreWeights,
} from '@/lib/api/admin/marketing';

/** Aliases expected by redesign notes (S6). */
const getScoreWeights = getMarketingScoreWeights;
const updateScoreWeights = updateMarketingScoreWeights;

export interface HoldingControlsBarProps {
  /** Called after a successful save so the parent can reload the holding board. */
  onSaved?: () => void;
  className?: string;
}

export function HoldingControlsBar({ onSaved, className }: HoldingControlsBarProps) {
  const [quota, setQuota] = useState<MarketingQuota | null>(null);
  const [textCap, setTextCap] = useState('6');
  const [videoCap, setVideoCap] = useState('3');
  const [weightViews, setWeightViews] = useState('0.1');
  const [weightComments, setWeightComments] = useState('1');
  const [weightVotes, setWeightVotes] = useState('0.5');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);

  const applyQuota = (data: MarketingQuota) => {
    setQuota(data);
    setTextCap(String(data.dailyTextCap));
    setVideoCap(String(data.dailyVideoCap));
  };

  const applyWeights = (data: MarketingScoreWeights) => {
    setWeightViews(String(data.weightViews));
    setWeightComments(String(data.weightComments));
    setWeightVotes(String(data.weightVotes));
  };

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [quotaData, weightsData] = await Promise.all([
        getMarketingQuota(),
        getScoreWeights(),
      ]);
      applyQuota(quotaData);
      applyWeights(weightsData);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`상한·가중치를 불러오지 못했습니다: ${msg}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const handleSave = async () => {
    const dailyTextCap = Number(textCap);
    const dailyVideoCap = Number(videoCap);
    const wViews = Number(weightViews);
    const wComments = Number(weightComments);
    const wVotes = Number(weightVotes);

    if (!Number.isInteger(dailyTextCap) || !Number.isInteger(dailyVideoCap)) {
      setError('상한은 정수여야 합니다.');
      return;
    }
    if (![wViews, wComments, wVotes].every((n) => Number.isFinite(n))) {
      setError('가중치는 숫자여야 합니다.');
      return;
    }

    setSaving(true);
    setError(null);
    setSavedMsg(null);
    try {
      const [quotaData, weightsData] = await Promise.all([
        updateMarketingQuota(dailyTextCap, dailyVideoCap),
        updateScoreWeights({
          weightViews: wViews,
          weightComments: wComments,
          weightVotes: wVotes,
        }),
      ]);
      applyQuota(quotaData);
      applyWeights(weightsData);
      setSavedMsg('저장했습니다. 보드가 재정렬됩니다.');
      onSaved?.();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`저장에 실패했습니다: ${msg}`);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Card className={`p-6 ${className ?? ''}`} data-testid="marketing-holding-controls">
        <div className="py-4 text-center text-gray-400">로드 중…</div>
      </Card>
    );
  }

  return (
    <Card className={`p-6 space-y-5 ${className ?? ''}`} data-testid="marketing-holding-controls">
      <div>
        <h3 className="font-semibold text-gray-800">대기 보드 설정</h3>
        <p className="mt-1 text-sm text-gray-500">
          자동 N위까지 · 표시 최대 20 · 핀은 상한 내 우선. 가중치·상한 저장 시 보드가 즉시 재정렬됩니다.
        </p>
      </div>

      <div className="space-y-3">
        <h4 className="text-sm font-medium text-gray-700">일일 상한</h4>
        <div className="grid grid-cols-2 gap-4 max-w-lg">
          <div className="space-y-1">
            <Label htmlFor="holding-dailyTextCap">공유 풀 (글 상한)</Label>
            <Input
              id="holding-dailyTextCap"
              type="number"
              min={1}
              max={50}
              value={textCap}
              onChange={(e) => setTextCap(e.target.value)}
              data-testid="holding-daily-text-cap"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="holding-dailyVideoCap">영상 상한</Label>
            <Input
              id="holding-dailyVideoCap"
              type="number"
              min={0}
              max={50}
              value={videoCap}
              onChange={(e) => setVideoCap(e.target.value)}
              data-testid="holding-daily-video-cap"
            />
          </div>
        </div>
        {quota && (
          <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-700 max-w-lg">
            오늘(KST): 영상 {quota.videosToday} · 글 {quota.textsToday} · 잔여 풀{' '}
            {quota.remainingPool} (컷라인 N≈{quota.remainingPool})
          </div>
        )}
      </div>

      <div className="space-y-3">
        <h4 className="text-sm font-medium text-gray-700">점수 가중치</h4>
        <p className="text-xs text-gray-500">점수 = 조회×w + 댓글×w + 투표×w (동점 → 최신)</p>
        <div className="grid grid-cols-3 gap-4 max-w-xl">
          <div className="space-y-1">
            <Label htmlFor="holding-weightViews">조회</Label>
            <Input
              id="holding-weightViews"
              type="number"
              step="0.1"
              min={0}
              max={100}
              value={weightViews}
              onChange={(e) => setWeightViews(e.target.value)}
              data-testid="holding-weight-views"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="holding-weightComments">댓글</Label>
            <Input
              id="holding-weightComments"
              type="number"
              step="0.1"
              min={0}
              max={100}
              value={weightComments}
              onChange={(e) => setWeightComments(e.target.value)}
              data-testid="holding-weight-comments"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="holding-weightVotes">투표</Label>
            <Input
              id="holding-weightVotes"
              type="number"
              step="0.1"
              min={0}
              max={100}
              value={weightVotes}
              onChange={(e) => setWeightVotes(e.target.value)}
              data-testid="holding-weight-votes"
            />
          </div>
        </div>
      </div>

      {error && (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </div>
      )}
      {savedMsg && (
        <div className="rounded border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-700">
          {savedMsg}
        </div>
      )}

      <div className="flex gap-2">
        <Button onClick={handleSave} disabled={saving} data-testid="holding-controls-save">
          {saving ? '저장 중…' : '저장'}
        </Button>
        <Button variant="outline" onClick={load} disabled={saving}>
          새로고침
        </Button>
      </div>
    </Card>
  );
}
