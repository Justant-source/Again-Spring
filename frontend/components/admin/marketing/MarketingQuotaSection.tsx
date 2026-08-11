'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  getMarketingQuota,
  updateMarketingPlatformQuota,
  MarketingQuota,
} from '@/lib/api/admin/marketing';

const PLATFORM_LABELS: Record<string, string> = {
  x_thread: 'X 스레드',
  instagram_feed: 'IG 피드',
  instagram_reels: 'IG 릴스',
  youtube_shorts: 'YT Shorts',
};

export function MarketingQuotaSection() {
  const [quota, setQuota] = useState<MarketingQuota | null>(null);
  const [xThread, setXThread] = useState('3');
  const [instagramFeed, setInstagramFeed] = useState('3');
  const [instagramReels, setInstagramReels] = useState('3');
  const [youtubeShorts, setYoutubeShorts] = useState('3');
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

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      applyQuota(await getMarketingQuota());
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`일일 상한을 불러오지 못했습니다: ${msg}`);
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
    if (!Object.values(caps).every((n) => Number.isInteger(n))) {
      setError('상한은 정수여야 합니다.');
      return;
    }
    setSaving(true);
    setError(null);
    setSavedMsg(null);
    try {
      applyQuota(await updateMarketingPlatformQuota(caps));
      setSavedMsg('저장했습니다.');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`저장에 실패했습니다: ${msg}`);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <Card className="p-6">
        <div className="py-4 text-center text-gray-400">로드 중…</div>
      </Card>
    );
  }

  return (
    <div className="space-y-4 max-w-2xl">
      <Card className="p-6 space-y-4">
        <div>
          <h3 className="font-semibold text-gray-800">플랫폼별 일일 상한</h3>
          <p className="mt-1 text-sm text-gray-500">
            Phase 2: 채널마다 독립 cap(기본 3). 같은 사연이 여러 플랫폼에 올라갈 수 있습니다.
            IG 피드와 릴스만 배타입니다.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          {(
            [
              ['x_thread', xThread, setXThread],
              ['instagram_feed', instagramFeed, setInstagramFeed],
              ['instagram_reels', instagramReels, setInstagramReels],
              ['youtube_shorts', youtubeShorts, setYoutubeShorts],
            ] as const
          ).map(([id, value, setValue]) => (
            <div key={id} className="space-y-1">
              <Label htmlFor={`cap-${id}`}>{PLATFORM_LABELS[id]}</Label>
              <Input
                id={`cap-${id}`}
                type="number"
                min={0}
                max={50}
                value={value}
                onChange={(e) => setValue(e.target.value)}
              />
            </div>
          ))}
        </div>

        {quota?.platforms && (
          <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-700 space-y-1">
            {Object.entries(quota.platforms).map(([id, p]) => (
              <div key={id}>
                {PLATFORM_LABELS[id] ?? id}: 오늘 {p.usedToday} / {p.cap} (잔여 {p.remaining})
              </div>
            ))}
          </div>
        )}

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
          <Button onClick={handleSave} disabled={saving}>
            {saving ? '저장 중…' : '저장'}
          </Button>
          <Button variant="outline" onClick={load} disabled={saving}>
            새로고침
          </Button>
        </div>
      </Card>
    </div>
  );
}
