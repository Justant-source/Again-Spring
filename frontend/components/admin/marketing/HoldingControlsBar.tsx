'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  getMarketingQuota,
  updateMarketingPlatformQuota,
  getMarketingScoreWeights,
  updateMarketingScoreWeights,
  type MarketingQuota,
  type MarketingScoreWeights,
} from '@/lib/api/admin/marketing';

const PLATFORM_LABELS: Record<string, string> = {
  x_thread: 'X',
  instagram_feed: 'IG 피드',
  instagram_reels: '릴스',
  youtube_shorts: 'Shorts',
};

export interface HoldingControlsBarProps {
  onSaved?: () => void;
  className?: string;
}

export function HoldingControlsBar({ onSaved, className }: HoldingControlsBarProps) {
  const [quota, setQuota] = useState<MarketingQuota | null>(null);
  const [xThread, setXThread] = useState('3');
  const [instagramFeed, setInstagramFeed] = useState('3');
  const [instagramReels, setInstagramReels] = useState('3');
  const [youtubeShorts, setYoutubeShorts] = useState('3');
  const [weightViews, setWeightViews] = useState('0.1');
  const [weightComments, setWeightComments] = useState('1');
  const [weightVotes, setWeightVotes] = useState('0.5');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);

  const applyQuota = (data: MarketingQuota) => {
    setQuota(data);
    const p = data.platforms;
    setXThread(String(p?.x_thread?.cap ?? 3));
    setInstagramFeed(String(p?.instagram_feed?.cap ?? 3));
    setInstagramReels(String(p?.instagram_reels?.cap ?? 3));
    setYoutubeShorts(String(p?.youtube_shorts?.cap ?? 3));
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
        getMarketingScoreWeights(),
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
    const caps = {
      xThread: Number(xThread),
      instagramFeed: Number(instagramFeed),
      instagramReels: Number(instagramReels),
      youtubeShorts: Number(youtubeShorts),
    };
    const wViews = Number(weightViews);
    const wComments = Number(weightComments);
    const wVotes = Number(weightVotes);

    if (!Object.values(caps).every((n) => Number.isInteger(n))) {
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
        updateMarketingPlatformQuota(caps),
        updateMarketingScoreWeights({
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
          Phase 2: 플랫폼별 일일 cap. 보드 미리보기는 조회·댓글·투표 가중치로 정렬합니다.
          커밋 선정은 플랫폼별 점수를 씁니다.
        </p>
      </div>

      <div className="space-y-3">
        <h4 className="text-sm font-medium text-gray-700">플랫폼별 일일 상한</h4>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 max-w-3xl">
          {(
            [
              ['x_thread', xThread, setXThread, 'holding-cap-x'],
              ['instagram_feed', instagramFeed, setInstagramFeed, 'holding-cap-feed'],
              ['instagram_reels', instagramReels, setInstagramReels, 'holding-cap-reels'],
              ['youtube_shorts', youtubeShorts, setYoutubeShorts, 'holding-cap-shorts'],
            ] as const
          ).map(([id, value, setValue, testId]) => (
            <div key={id} className="space-y-1">
              <Label htmlFor={testId}>{PLATFORM_LABELS[id]}</Label>
              <Input
                id={testId}
                type="number"
                min={0}
                max={50}
                value={value}
                onChange={(e) => setValue(e.target.value)}
                data-testid={testId}
              />
            </div>
          ))}
        </div>
        {quota?.platforms && (
          <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-700 max-w-3xl">
            오늘(KST):{' '}
            {Object.entries(quota.platforms)
              .map(([id, p]) => `${PLATFORM_LABELS[id] ?? id} ${p.usedToday}/${p.cap}`)
              .join(' · ')}
          </div>
        )}
      </div>

      <div className="space-y-3">
        <h4 className="text-sm font-medium text-gray-700">보드 미리보기 가중치</h4>
        <p className="text-xs text-gray-500">
          대기 보드 정렬용(legacy). 커밋 점수 가중치는 API{' '}
          <code className="text-xs">/score-weights</code> platforms 맵으로 조정합니다.
        </p>
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
