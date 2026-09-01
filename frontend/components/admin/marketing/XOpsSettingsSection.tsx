'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  getMarketingXOpsSettings,
  updateMarketingXOpsSettings,
  runMarketingXOpsPersonaLearn,
  MarketingXOpsSettings,
} from '@/lib/api/admin/marketing';

const DEFAULTS: MarketingXOpsSettings = {
  morningTime: '07:30',
  nightTime: '22:00',
  storyScoopsPerDay: 2,
  outboundDailyCap: 20,
  inboundDailyCap: 40,
  inboundPerPostCap: 12,
  hotMinReplies: 3,
  hotMaxAgeHours: 6,
  ritualEnabled: false,
  inboundEnabled: false,
  outboundEnabled: false,
  personaLearningEnabled: true,
  personaLearnAt: '04:30',
};

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

function FlagSwitch({
  id,
  label,
  hint,
  checked,
  onChange,
}: {
  id: string;
  label: string;
  hint: string;
  checked: boolean;
  onChange: () => void;
}) {
  return (
    <div className="flex items-start justify-between gap-3 rounded border border-gray-200 px-3 py-3">
      <div>
        <div className="text-sm font-medium text-gray-800">{label}</div>
        <p className="mt-0.5 text-xs text-gray-500">{hint}</p>
      </div>
      <button
        type="button"
        id={id}
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={onChange}
        className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ${
          checked ? 'bg-[#5F8F76]' : 'bg-gray-300'
        }`}
      >
        <span
          className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow transition-transform ${
            checked ? 'translate-x-5' : 'translate-x-0'
          }`}
        />
      </button>
    </div>
  );
}

export function XOpsSettingsSection() {
  const [settings, setSettings] = useState<MarketingXOpsSettings>(DEFAULTS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [learning, setLearning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedMsg, setSavedMsg] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setSettings(await getMarketingXOpsSettings());
    } catch (err: unknown) {
      setError(`X 운영 설정을 불러오지 못했습니다: ${extractError(err)}`);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const patch = <K extends keyof MarketingXOpsSettings>(key: K, value: MarketingXOpsSettings[K]) => {
    setSettings((prev) => ({ ...prev, [key]: value }));
  };

  const writable = (s: MarketingXOpsSettings) => ({
    morningTime: s.morningTime,
    nightTime: s.nightTime,
    storyScoopsPerDay: s.storyScoopsPerDay,
    outboundDailyCap: s.outboundDailyCap,
    inboundDailyCap: s.inboundDailyCap,
    inboundPerPostCap: s.inboundPerPostCap,
    hotMinReplies: s.hotMinReplies,
    hotMaxAgeHours: s.hotMaxAgeHours,
    ritualEnabled: s.ritualEnabled,
    inboundEnabled: s.inboundEnabled,
    outboundEnabled: s.outboundEnabled,
    personaLearningEnabled: s.personaLearningEnabled,
    personaLearnAt: s.personaLearnAt,
  });

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setSavedMsg(null);
    try {
      setSettings(await updateMarketingXOpsSettings(writable(settings)));
      setSavedMsg(
        '저장했습니다. 글·댓글은 해당 스위치가 켜져 있을 때만 나갑니다. dev는 LLM이 꺼져 있어(L3) 작문·발행은 동작하지 않습니다. 페르소나 학습은 새벽 시각에 돌아갑니다.'
      );
    } catch (err: unknown) {
      setError(`저장에 실패했습니다: ${extractError(err)}`);
    } finally {
      setSaving(false);
    }
  };

  const handleLearnNow = async () => {
    setLearning(true);
    setError(null);
    setSavedMsg(null);
    try {
      setSettings(await runMarketingXOpsPersonaLearn());
      setSavedMsg('학습을 돌렸습니다. 아래 상태가 갱신됩니다. (dev는 LLM 없이 댓글만 모읍니다)');
    } catch (err: unknown) {
      setError(`학습에 실패했습니다: ${extractError(err)}`);
    } finally {
      setLearning(false);
    }
  };

  if (loading) {
    return (
      <div data-testid="marketing-x-ops-section">
        <Card className="p-6">
          <div className="py-4 text-center text-gray-400">로드 중…</div>
        </Card>
      </div>
    );
  }

  return (
    <div data-testid="marketing-x-ops-section" className="space-y-4 max-w-2xl">
      <Card className="p-6 space-y-5">
        <div>
          <h3 className="font-semibold text-gray-800">X 운영</h3>
          <p className="mt-1 text-sm text-gray-500">
            아침·밤 의식 글, 사연 퍼오기, 우리 글 대댓글, 맞팔 선댓글 한도입니다. 저장은 바로
            반영됩니다. 글·댓글은 해당 스위치가 켜져 있을 때만 나갑니다. dev는 LLM이 꺼져
            있어(L3) 작문·발행은 동작하지 않습니다. 페르소나 학습은 새벽 시각에 그대로
            돌아갑니다. 나중에 켤 때는 우리 글 대댓글 → 맞팔 선댓글 → 아침/밤 글 순을
            권합니다.
          </p>
        </div>

        <div className="space-y-2">
          <FlagSwitch
            id="x-ops-ritual"
            label="아침/밤 글"
            hint="07:30·22:00 KST 사진 한 장 + 짧은 격려"
            checked={settings.ritualEnabled}
            onChange={() => patch('ritualEnabled', !settings.ritualEnabled)}
          />
          <FlagSwitch
            id="x-ops-inbound"
            label="우리 글 대댓글"
            hint="한도는 하루·글당 숫자"
            checked={settings.inboundEnabled}
            onChange={() => patch('inboundEnabled', !settings.inboundEnabled)}
          />
          <FlagSwitch
            id="x-ops-outbound"
            label="맞팔 선댓글"
            hint="맞팔·불 난 글만. 주간(08:00–22:00 KST) 30분마다 후보 1회, 틱당 댓글 1개"
            checked={settings.outboundEnabled}
            onChange={() => patch('outboundEnabled', !settings.outboundEnabled)}
          />
          <FlagSwitch
            id="x-ops-persona-learn"
            label="페르소나 학습"
            hint="매일 새벽에 내가 단 댓글만 골라 목소리를 갱신. 자동 스레드는 제외"
            checked={settings.personaLearningEnabled}
            onChange={() => patch('personaLearningEnabled', !settings.personaLearningEnabled)}
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-1">
            <Label htmlFor="x-ops-morning">아침 시각 (KST)</Label>
            <Input
              id="x-ops-morning"
              type="time"
              value={settings.morningTime}
              onChange={(e) => patch('morningTime', e.target.value)}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-night">밤 시각 (KST)</Label>
            <Input
              id="x-ops-night"
              type="time"
              value={settings.nightTime}
              onChange={(e) => patch('nightTime', e.target.value)}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-learn-at">페르소나 학습 시각 (KST)</Label>
            <Input
              id="x-ops-learn-at"
              type="time"
              value={settings.personaLearnAt}
              onChange={(e) => patch('personaLearnAt', e.target.value)}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-scoops">사연 퍼오기 /일</Label>
            <Input
              id="x-ops-scoops"
              type="number"
              min={0}
              max={10}
              value={settings.storyScoopsPerDay}
              onChange={(e) => patch('storyScoopsPerDay', Number(e.target.value))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-outbound-cap">선댓글 /일</Label>
            <Input
              id="x-ops-outbound-cap"
              type="number"
              min={0}
              max={100}
              value={settings.outboundDailyCap}
              onChange={(e) => patch('outboundDailyCap', Number(e.target.value))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-inbound-cap">우리 글 대댓글 /일</Label>
            <Input
              id="x-ops-inbound-cap"
              type="number"
              min={0}
              max={200}
              value={settings.inboundDailyCap}
              onChange={(e) => patch('inboundDailyCap', Number(e.target.value))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-per-post">우리 글당 대댓글</Label>
            <Input
              id="x-ops-per-post"
              type="number"
              min={0}
              max={50}
              value={settings.inboundPerPostCap}
              onChange={(e) => patch('inboundPerPostCap', Number(e.target.value))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-hot-min">최소 댓글 (0=제한 없음)</Label>
            <Input
              id="x-ops-hot-min"
              type="number"
              min={0}
              max={50}
              value={settings.hotMinReplies}
              onChange={(e) => patch('hotMinReplies', Number(e.target.value))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="x-ops-hot-age">불 난 글 최대 나이 (시간)</Label>
            <Input
              id="x-ops-hot-age"
              type="number"
              min={1}
              max={48}
              value={settings.hotMaxAgeHours}
              onChange={(e) => patch('hotMaxAgeHours', Number(e.target.value))}
            />
          </div>
        </div>

        <div
          data-testid="marketing-x-ops-persona-learn"
          className="space-y-2 rounded border border-gray-200 px-3 py-3"
        >
          <div className="text-sm font-medium text-gray-800">학습 상태</div>
          <p className="text-xs text-gray-500">
            마지막 {settings.personaLastStatus ?? 'NEVER'}
            {settings.personaLastNewCount != null
              ? ` · 새 댓글 ${settings.personaLastNewCount}건`
              : ''}
            {settings.personaLastLearnedAt
              ? ` · ${settings.personaLastLearnedAt}`
              : ''}
            {settings.personaDrillToday != null
              ? ` · 오늘 드릴 ${settings.personaDrillToday}건`
              : ''}
          </p>
          {settings.personaSummary && (
            <p className="text-sm text-gray-700">{settings.personaSummary}</p>
          )}
          <Button
            type="button"
            variant="outline"
            onClick={() => void handleLearnNow()}
            disabled={saving || learning || !settings.personaLearningEnabled}
          >
            {learning ? '학습 중…' : '지금 학습'}
          </Button>
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
          <Button onClick={() => void handleSave()} disabled={saving || learning}>
            {saving ? '저장 중…' : '저장'}
          </Button>
          <Button variant="outline" onClick={() => void load()} disabled={saving || learning}>
            새로고침
          </Button>
        </div>
      </Card>
    </div>
  );
}
