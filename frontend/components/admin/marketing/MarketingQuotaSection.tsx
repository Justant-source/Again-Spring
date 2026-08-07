'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  getMarketingQuota,
  updateMarketingQuota,
  MarketingQuota,
} from '@/lib/api/admin/marketing';

export function MarketingQuotaSection() {
  const [quota, setQuota] = useState<MarketingQuota | null>(null);
  const [textCap, setTextCap] = useState('6');
  const [videoCap, setVideoCap] = useState('3');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getMarketingQuota();
      setQuota(data);
      setTextCap(String(data.dailyTextCap));
      setVideoCap(String(data.dailyVideoCap));
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
    const dailyTextCap = Number(textCap);
    const dailyVideoCap = Number(videoCap);
    if (!Number.isInteger(dailyTextCap) || !Number.isInteger(dailyVideoCap)) {
      setError('상한은 정수여야 합니다.');
      return;
    }
    setSaving(true);
    setError(null);
    setSavedMsg(null);
    try {
      const data = await updateMarketingQuota(dailyTextCap, dailyVideoCap);
      setQuota(data);
      setTextCap(String(data.dailyTextCap));
      setVideoCap(String(data.dailyVideoCap));
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
    <div className="space-y-4 max-w-lg">
      <Card className="p-6 space-y-4">
        <div>
          <h3 className="font-semibold text-gray-800">24h 자동 분배 일일 상한</h3>
          <p className="mt-1 text-sm text-gray-500">
            영상 우선. 글 슬롯 = 글 상한 − 오늘 영상. 글 = X 스레드 + IG 피드, 영상 = 릴스 + 쇼츠(X 없음).
            수동 잡도 같은 날 카운트에 포함됩니다.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <Label htmlFor="dailyTextCap">일일 글 상한 (공유 풀)</Label>
            <Input
              id="dailyTextCap"
              type="number"
              min={1}
              max={50}
              value={textCap}
              onChange={(e) => setTextCap(e.target.value)}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="dailyVideoCap">일일 영상 상한</Label>
            <Input
              id="dailyVideoCap"
              type="number"
              min={0}
              max={50}
              value={videoCap}
              onChange={(e) => setVideoCap(e.target.value)}
            />
          </div>
        </div>

        {quota && (
          <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-700">
            오늘(KST): 영상 {quota.videosToday} · 글 {quota.textsToday} · 잔여 풀{' '}
            {quota.remainingPool}
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
