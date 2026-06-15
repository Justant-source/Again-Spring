'use client';

import { useState, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AdminSection } from '@/components/admin/AdminSection';
import { ActionFeed } from '@/components/admin/ai-user/ActionFeed';
import { PersonaPerformanceTable } from '@/components/admin/ai-user/PersonaPerformanceTable';
import { HourlyDistributionChart } from '@/components/admin/ai-user/HourlyDistributionChart';
import {
  getGenerationConfig,
  updateGenerationConfig,
  killAllBackends,
  getGenerationStatus,
  type GenerationConfig,
  type Backend,
  type UpdateConfigRequest,
  type EstimateResult,
  type GenerationStatus,
} from '@/lib/api/admin/ai-user';
import {
  Cpu, Zap, Power, Save, AlertTriangle, AlertCircle, Info, RefreshCw,
} from 'lucide-react';
import { AnthropicApiKeyPanel, AnthropicBaseUrlPanel } from '@/components/admin/ai-rules/AnthropicApiPanels';

// ── §11.5 클라이언트 측 추정 상수 ────────────────────────────────────────
// 실측 기준 (ClaudeApiInvoker 로그 avg): input ~4600, output ~100
const PERCALL = {
  post:    { in: 4_800, out: 300  },  // self-critique 포함 약간 높음
  comment: { in: 4_600, out: 100  },
  reply:   { in: 4_300, out: 80   },
};
const MAX5X_DAILY  = 2_100_000;  // Max 5x = Pro(420K) × 5
const MAX5X_WINDOW = 440_000;    // Max 5x = Pro(88K) × 5
const PEAK_SHARE   = 0.208;      // 균등분포 기준 5h/24h
const HAIKU_IN_RATE  = 1.0;   // $/Mtok
const HAIKU_OUT_RATE = 5.0;   // $/Mtok
const CACHE_FACTOR   = 0.235;

function computeEstimate(
  posts: number, comments: number, replies: number,
  bPost: Backend, bComment: Backend, bReply: Backend,
  caching: boolean,
): EstimateResult {
  let calls = 0, cliIn = 0, cliOut = 0, apiIn = 0, apiOut = 0;

  const add = (t: number, b: Backend, p: { in: number; out: number }) => {
    if (b === 'OFF' || t <= 0) return;
    calls += t;
    if (b === 'CLI') { cliIn += t * p.in; cliOut += t * p.out; }
    else             { apiIn += t * p.in; apiOut += t * p.out; }
  };
  add(posts, bPost, PERCALL.post);
  add(comments, bComment, PERCALL.comment);
  add(replies, bReply, PERCALL.reply);

  const cliTokensTotal = cliIn + cliOut;
  const cliPct      = (cliTokensTotal / MAX5X_DAILY) * 100;
  const cliPeakPct  = (cliTokensTotal * PEAK_SHARE / MAX5X_WINDOW) * 100;
  const apiInEff    = caching ? apiIn * CACHE_FACTOR : apiIn;
  const apiCostDay  = (apiInEff / 1_000_000) * HAIKU_IN_RATE + (apiOut / 1_000_000) * HAIKU_OUT_RATE;
  const apiCostMonth = apiCostDay * 30;
  const apiTokensTotal = apiIn + apiOut;

  const warnings: string[] = [];
  if (calls === 0) {
    warnings.push('INFO:생성 없음 — 모든 타입이 OFF 또는 목표량 0');
  } else {
    if (cliPct > 100)      warnings.push('DANGER:CLI 경로가 Max 5x 일일 한도 초과 — 종일 throttle 위험');
    else if (cliPct > 80)  warnings.push('WARN:CLI 경로가 Max 5x 한도 80% 초과 — 개발 quota 공유 주의');
    if (cliPeakPct > 100)  warnings.push('WARN:저녁 피크 5h 윈도우 초과 — 피크 시간대 throttle 위험');
  }

  return { callsPerDay: calls, cliTokensTotal, cliPct, cliPeakPct, apiCostDay, apiCostMonth, apiTokensTotal, warnings };
}

// ── 경고 배지 ──────────────────────────────────────────────────────────────
function WarningBadges({ warnings }: { warnings: string[] }) {
  if (warnings.length === 0) return null;
  return (
    <div className="space-y-1.5">
      {warnings.map((w, i) => {
        const [level, msg] = w.split(/:(.+)/);
        const colors =
          level === 'DANGER' ? 'bg-red-50 border-red-200 text-red-700' :
          level === 'WARN'   ? 'bg-amber-50 border-amber-200 text-amber-700' :
                               'bg-blue-50 border-blue-200 text-blue-700';
        const Icon = level === 'DANGER' ? AlertTriangle :
                     level === 'WARN'   ? AlertCircle : Info;
        return (
          <div key={i} className={`flex items-start gap-2 rounded-md border px-3 py-2 text-xs ${colors}`}>
            <Icon className="h-3.5 w-3.5 mt-0.5 shrink-0" />
            <span>{msg}</span>
          </div>
        );
      })}
    </div>
  );
}

// ── 게이지 바 ─────────────────────────────────────────────────────────────
function GaugeBar({ pct, danger = 100, warn = 80 }: { pct: number; danger?: number; warn?: number }) {
  const clamped = Math.min(pct, 100);
  const color =
    pct > danger ? 'bg-red-500' :
    pct > warn   ? 'bg-amber-500' : 'bg-emerald-500';
  return (
    <div className="h-2 w-full rounded-full bg-gray-100 overflow-hidden">
      <div className={`h-full rounded-full transition-all duration-300 ${color}`} style={{ width: `${clamped}%` }} />
    </div>
  );
}

// ── 백엔드 선택 (CLI / API / OFF) ─────────────────────────────────────────
function BackendSelector({
  label, value, onChange,
}: { label: string; value: Backend; onChange: (v: Backend) => void }) {
  const badge = value === 'CLI'  ? 'bg-blue-100 text-blue-700 border-blue-200' :
                value === 'API'  ? 'bg-violet-100 text-violet-700 border-violet-200' :
                                   'bg-gray-100 text-gray-500 border-gray-200';
  return (
    <div className="flex items-center gap-4 py-2 border-b last:border-b-0">
      <span className="w-20 text-sm font-medium text-gray-700 shrink-0">{label}</span>
      <div className="flex items-center gap-5">
        {(['CLI', 'API', 'OFF'] as Backend[]).map(opt => (
          <label key={opt} className="flex items-center gap-1.5 cursor-pointer text-sm">
            <input
              type="radio"
              name={`backend-${label}`}
              value={opt}
              checked={value === opt}
              onChange={() => onChange(opt)}
              className="accent-blue-600"
            />
            <span className={opt === 'OFF' ? 'text-gray-400' : ''}>{opt}</span>
          </label>
        ))}
      </div>
      <span className={`ml-auto text-[11px] font-medium border rounded px-2 py-0.5 ${badge}`}>
        {value}
      </span>
    </div>
  );
}

// ── 슬라이더 ──────────────────────────────────────────────────────────────
function SliderRow({
  label, value, min = 0, max, step = 1, onChange, auto, onToggleAuto, badge, disabled,
}: {
  label: string; value: number; min?: number; max: number; step?: number;
  onChange: (v: number) => void; auto?: boolean; onToggleAuto?: () => void;
  badge?: string; disabled?: boolean;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <Label className="text-sm font-medium">{label}</Label>
        <div className="flex items-center gap-2">
          {badge && (
            <span className="text-[11px] text-gray-400">{badge}</span>
          )}
          {onToggleAuto !== undefined && (
            <button
              onClick={onToggleAuto}
              className={`text-[11px] px-2 py-0.5 rounded border transition-colors ${
                auto ? 'bg-blue-50 border-blue-200 text-blue-600' : 'bg-gray-50 border-gray-200 text-gray-500'
              }`}
            >
              {auto ? '자동' : '수동'}
            </button>
          )}
          <Input
            type="number"
            value={value}
            min={min}
            max={max}
            step={step}
            disabled={auto || disabled}
            onChange={e => onChange(Math.max(min, Math.min(max, Number(e.target.value))))}
            className="w-20 h-7 text-right text-sm"
          />
        </div>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        disabled={auto || disabled}
        onChange={e => onChange(Number(e.target.value))}
        className="w-full accent-blue-600"
      />
    </div>
  );
}

// ── 메인 페이지 ───────────────────────────────────────────────────────────
export default function AiUserPage() {
  const [loading, setLoading]     = useState(true);
  const [saving, setSaving]       = useState(false);
  const [killing, setKilling]     = useState(false);
  const [error, setError]         = useState('');
  const [savedMsg, setSavedMsg]   = useState('');
  const [serverEstimate, setServerEstimate] = useState<EstimateResult | null>(null);

  // ── 폼 상태 ─────────────────────────────────────────────────────────
  const [posts,    setPosts]    = useState(0);
  const [comments, setComments] = useState(0);
  const [replies,  setReplies]  = useState(0);
  const [votes,    setVotes]    = useState(0);
  const [likes,    setLikes]    = useState(0);
  const [autoComment, setAutoComment] = useState(true);
  const [autoReply,   setAutoReply]   = useState(true);
  const [bPost,    setBPost]    = useState<Backend>('OFF');
  const [bComment, setBComment] = useState<Backend>('OFF');
  const [bReply,   setBReply]   = useState<Backend>('OFF');
  const [caching,  setCaching]  = useState(true);
  const [budget,   setBudget]   = useState<string>('');
  const [ratios,   setRatios]   = useState({ comment: 7.6, reply: 4.4, vote: 6.5, like: 15.7 });

  // ── 진행 현황 상태 ───────────────────────────────────────────────
  const [genStatus, setGenStatus] = useState<GenerationStatus | null>(null);
  const [statusLoading, setStatusLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(false);

  // ── 초기 로드 ─────────────────────────────────────────────────────
  const loadConfig = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const cfg = await getGenerationConfig();
      applyConfig(cfg);
    } catch (e: any) {
      setError('설정 로드 실패: ' + (e?.message ?? '알 수 없는 오류'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadConfig(); }, [loadConfig]);

  function applyConfig(cfg: GenerationConfig) {
    setPosts(cfg.targetPosts); setComments(cfg.targetComments); setReplies(cfg.targetReplies);
    setVotes(cfg.targetVotes); setLikes(cfg.targetLikes);
    setAutoComment(cfg.autoComment); setAutoReply(cfg.autoReply);
    setBPost(cfg.backendPost); setBComment(cfg.backendComment); setBReply(cfg.backendReply);
    setCaching(cfg.promptCaching);
    setBudget(cfg.dailyTokenBudget != null ? String(cfg.dailyTokenBudget) : '');
    setRatios({ comment: cfg.ratioComment, reply: cfg.ratioReply, vote: cfg.ratioVote, like: cfg.ratioLike });
    setServerEstimate(cfg.estimate);
  }

  // ── 자동 비율 연동 ─────────────────────────────────────────────────
  const handlePostsChange = (v: number) => {
    setPosts(v);
    if (autoComment) setComments(Math.round(v * ratios.comment));
    if (autoReply)   setReplies(Math.min(900, Math.round(v * ratios.reply)));
    setVotes(Math.round(v * ratios.vote));
    setLikes(Math.round(v * ratios.like));
  };

  // ── 클라이언트 측 실시간 추정 ─────────────────────────────────────
  const liveEstimate = computeEstimate(posts, comments, replies, bPost, bComment, bReply, caching);
  const est = serverEstimate ?? liveEstimate;

  // ── 저장 ─────────────────────────────────────────────────────────
  const handleSave = async () => {
    setSaving(true); setError(''); setSavedMsg('');
    const req: UpdateConfigRequest = {
      targetPosts: posts, targetComments: comments, targetReplies: replies,
      targetVotes: votes, targetLikes: likes,
      autoComment, autoReply,
      backendPost: bPost, backendComment: bComment, backendReply: bReply,
      promptCaching: caching,
      dailyTokenBudget: budget !== '' ? Number(budget) : null,
    };
    try {
      const cfg = await updateGenerationConfig(req);
      applyConfig(cfg);
      setSavedMsg('저장 완료 ✓');
      setTimeout(() => setSavedMsg(''), 3000);
    } catch (e: any) {
      setError('저장 실패: ' + (e?.response?.data?.message ?? e?.message ?? '알 수 없는 오류'));
    } finally {
      setSaving(false);
    }
  };

  // ── 비상 정지 ────────────────────────────────────────────────────
  const handleKill = async () => {
    if (!confirm('모든 AI 생성 백엔드를 OFF로 설정합니다. 계속하시겠습니까?')) return;
    setKilling(true); setError('');
    try {
      await killAllBackends();
      await loadConfig();
      setSavedMsg('비상 정지 완료 — 모든 backend OFF');
      setTimeout(() => setSavedMsg(''), 5000);
    } catch (e: any) {
      setError('비상 정지 실패: ' + (e?.message ?? '알 수 없는 오류'));
    } finally {
      setKilling(false);
    }
  };

  // ── 진행 현황 조회 ────────────────────────────────────────────────
  const fetchStatus = useCallback(async () => {
    setStatusLoading(true);
    try {
      const s = await getGenerationStatus();
      setGenStatus(s);
    } catch (e) {
      // 실패 시 이전 값 유지
    } finally {
      setStatusLoading(false);
    }
  }, []);

  // ── 자동 새로고침 ──────────────────────────────────────────────────
  useEffect(() => {
    fetchStatus(); // initial load
    if (!autoRefresh) return;
    const id = setInterval(fetchStatus, 60_000);
    return () => clearInterval(id);
  }, [autoRefresh, fetchStatus]);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-400">
        <RefreshCw className="h-6 w-6 animate-spin mr-2" /> 로드 중...
      </div>
    );
  }

  const allOff = bPost === 'OFF' && bComment === 'OFF' && bReply === 'OFF';

  return (
    <div className="space-y-6 max-w-6xl">

      {/* 페이지 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Cpu className="h-6 w-6 text-blue-600" />
            AI 생성 관제
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            일일 생성량·백엔드를 실시간 조정하고 토큰·비용 추정을 확인합니다.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge className={allOff ? 'bg-gray-100 text-gray-500 border-gray-200' : 'bg-emerald-100 text-emerald-700 border-emerald-200'}>
            {allOff ? '전체 OFF' : '활성'}
          </Badge>
          <span className="text-sm text-gray-400">오늘 {est.callsPerDay} 콜 예상</span>
        </div>
      </div>

      {/* 에러 / 성공 메시지 */}
      {error    && <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {savedMsg && <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{savedMsg}</div>}

      {/* 탭 구조 */}
      <Tabs defaultValue="settings" className="w-full">
        <TabsList>
          <TabsTrigger value="settings">생성 설정</TabsTrigger>
          <TabsTrigger value="monitor">실시간 관제</TabsTrigger>
          <TabsTrigger value="api-settings">API 설정</TabsTrigger>
        </TabsList>

        <TabsContent value="settings" className="mt-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* ── 왼쪽 2/3: 설정 영역 ─────────────────────────────────── */}
        <div className="lg:col-span-2 space-y-6">

          {/* 일일 생성 목표량 */}
          <AdminSection title="일일 생성 목표량">
            <div className="space-y-5 px-1">
              <SliderRow
                label="글 (POST)"
                value={posts} min={0} max={100}
                onChange={handlePostsChange}
                badge="기준 슬라이더 — 댓글·대댓글 자동 연동"
              />
              <SliderRow
                label="댓글 (COMMENT)"
                value={comments} min={0} max={1200}
                onChange={v => setComments(v)}
                auto={autoComment}
                onToggleAuto={() => setAutoComment(p => !p)}
                badge={`글 × ${ratios.comment}`}
              />
              <SliderRow
                label="대댓글 (REPLY)"
                value={replies} min={0} max={900}
                onChange={v => setReplies(v)}
                auto={autoReply}
                onToggleAuto={() => setAutoReply(p => !p)}
                badge={`글 × ${ratios.reply}`}
              />
              <div className="border-t pt-4 grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <Label className="text-xs text-gray-400">투표 (LLM 미사용)</Label>
                  <div className="text-lg font-mono font-semibold text-gray-600">{votes.toLocaleString()}</div>
                  <div className="text-[11px] text-gray-400">글 × {ratios.vote}</div>
                </div>
                <div className="space-y-1">
                  <Label className="text-xs text-gray-400">좋아요 (LLM 미사용)</Label>
                  <div className="text-lg font-mono font-semibold text-gray-600">{likes.toLocaleString()}</div>
                  <div className="text-[11px] text-gray-400">글 × {ratios.like}</div>
                </div>
              </div>
            </div>
          </AdminSection>

          {/* 백엔드 라우팅 */}
          <AdminSection title="백엔드 라우팅">
            <div className="px-1">
              <div className="text-xs text-gray-400 mb-3">
                콘텐츠 타입별 독립 설정. CLI·API 중 하나만 선택 가능, 둘 다 해제 시 OFF.
              </div>
              <BackendSelector label="POST"    value={bPost}    onChange={setBPost} />
              <BackendSelector label="COMMENT" value={bComment} onChange={setBComment} />
              <BackendSelector label="REPLY"   value={bReply}   onChange={setBReply} />
              <div className="mt-4 rounded bg-gray-50 border border-gray-200 p-3 text-xs text-gray-500 space-y-1">
                <div><span className="font-medium text-blue-600">CLI</span> — 호스트 ~/.claude 마운트, Max 5x 구독 quota 차감 (개발 quota와 공유)</div>
                <div><span className="font-medium text-violet-600">API</span> — Anthropic Messages API, 종량 과금 ($1/$5 per Mtok, 캐싱 시 입력 ~23.5%)</div>
                <div><span className="font-medium text-gray-500">OFF</span> — 해당 타입 생성 없음, 토큰 0</div>
              </div>
            </div>
          </AdminSection>

          {/* API 옵션 */}
          <AdminSection title="API 옵션">
            <div className="px-1 space-y-4">
              <div className="flex items-center gap-3">
                <Checkbox
                  id="caching"
                  checked={caching}
                  onCheckedChange={v => setCaching(Boolean(v))}
                />
                <label htmlFor="caching" className="text-sm cursor-pointer">
                  프롬프트 캐싱 활성화
                  <span className="ml-2 text-xs text-gray-400">(API 경로만 적용, 시스템 프롬프트 고정 — 입력 토큰 ~76.5% 절감)</span>
                </label>
              </div>
              <div className="flex items-center gap-3">
                <Label htmlFor="budget" className="text-sm shrink-0 w-36">일일 토큰 예산</Label>
                <Input
                  id="budget"
                  type="number"
                  placeholder="없음 (무제한)"
                  value={budget}
                  onChange={e => setBudget(e.target.value)}
                  className="w-40 text-sm"
                />
                <span className="text-xs text-gray-400">초과 시 해당 경로 자동 OFF</span>
              </div>
            </div>
          </AdminSection>

        </div>

        {/* ── 오른쪽 1/3: 실시간 예상 소비 패널 ────────────────────── */}
        <div className="lg:col-span-1">
          <div className="sticky top-6 space-y-4">

            <div className="rounded-xl border border-gray-200 bg-white shadow-sm">
              <div className="flex items-center gap-2 border-b px-4 py-3">
                <Zap className="h-4 w-4 text-amber-500" />
                <span className="font-semibold text-sm">실시간 예상 소비</span>
                <span className="ml-auto text-xs text-gray-400">§11.5 추정</span>
              </div>

              <div className="px-4 py-4 space-y-5">

                {/* 호출 수 */}
                <div>
                  <div className="text-xs text-gray-400 mb-1">호출 / 일</div>
                  <div className="text-2xl font-mono font-bold text-gray-800">
                    {liveEstimate.callsPerDay.toLocaleString()}
                    <span className="text-sm font-normal text-gray-400 ml-1">콜</span>
                  </div>
                </div>

                {/* CLI 경로 */}
                <div className="space-y-2">
                  <div className="text-xs font-medium text-blue-600 flex items-center gap-1">
                    <span className="h-2 w-2 rounded-full bg-blue-500 inline-block" />
                    CLI 경로 (Max 5x)
                  </div>
                  <div className="flex justify-between text-xs text-gray-500">
                    <span>{(liveEstimate.cliTokensTotal / 1000).toFixed(0)}K 토큰</span>
                    <span className={
                      liveEstimate.cliPct > 100 ? 'text-red-600 font-bold' :
                      liveEstimate.cliPct > 80  ? 'text-amber-600 font-semibold' : 'text-gray-600'
                    }>
                      {liveEstimate.cliPct.toFixed(1)}%
                    </span>
                  </div>
                  <GaugeBar pct={liveEstimate.cliPct} danger={100} warn={80} />
                  <div className="flex justify-between text-[11px] text-gray-400">
                    <span>피크(5h): {liveEstimate.cliPeakPct.toFixed(1)}%</span>
                    <span>한도: 420K/일</span>
                  </div>
                </div>

                {/* API 경로 */}
                <div className="space-y-1.5">
                  <div className="text-xs font-medium text-violet-600 flex items-center gap-1">
                    <span className="h-2 w-2 rounded-full bg-violet-500 inline-block" />
                    API 경로
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500 text-xs">$/일</span>
                    <span className="font-mono font-semibold text-gray-700">
                      ${liveEstimate.apiCostDay.toFixed(2)}
                    </span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500 text-xs">$/월 (×30)</span>
                    <span className="font-mono font-semibold text-gray-700">
                      ${liveEstimate.apiCostMonth.toFixed(2)}
                    </span>
                  </div>
                  {caching && (
                    <div className="text-[11px] text-gray-400">
                      캐싱 적용 — 입력 × {(CACHE_FACTOR * 100).toFixed(1)}%
                    </div>
                  )}
                </div>

                {/* 경고 */}
                <WarningBadges warnings={liveEstimate.warnings} />

              </div>
            </div>

            {/* 버튼 영역 */}
            <div className="space-y-2">
              <Button
                className="w-full"
                onClick={handleSave}
                disabled={saving || killing}
              >
                <Save className="h-4 w-4 mr-2" />
                {saving ? '저장 중...' : '설정 저장'}
              </Button>
              <Button
                variant="outline"
                className="w-full border-red-200 text-red-600 hover:bg-red-50"
                onClick={handleKill}
                disabled={saving || killing}
              >
                <Power className="h-4 w-4 mr-2" />
                {killing ? '처리 중...' : '비상 정지 (전체 OFF)'}
              </Button>
            </div>

            {/* 비율 참고 */}
            <div className="rounded-lg bg-gray-50 border border-gray-200 p-3 text-[11px] text-gray-400 space-y-1">
              <div className="font-medium text-gray-500 mb-1">추천 비율 (글 기준)</div>
              <div className="grid grid-cols-2 gap-x-3">
                <span>댓글: ×{ratios.comment}</span>
                <span>대댓글: ×{ratios.reply}</span>
                <span>투표: ×{ratios.vote}</span>
                <span>좋아요: ×{ratios.like}</span>
              </div>
            </div>

          </div>
        </div>

        </div>

          {/* 오늘 진행 현황 */}
          <section className="mt-8 rounded-xl border border-gray-200 bg-white p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-semibold text-gray-800">오늘 진행 현황</h2>
              <div className="flex items-center gap-3">
                <label className="flex items-center gap-1.5 text-sm text-gray-500 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={autoRefresh}
                    onChange={e => setAutoRefresh(e.target.checked)}
                    className="rounded"
                    data-testid="ai-gen-status-auto-refresh"
                  />
                  자동 새로고침 (60초)
                </label>
                <button
                  onClick={fetchStatus}
                  disabled={statusLoading}
                  className="text-sm px-3 py-1.5 rounded-lg border border-gray-300 hover:bg-gray-50 disabled:opacity-50"
                  data-testid="ai-gen-status-refresh-btn"
                >
                  {statusLoading ? '...' : '새로고침'}
                </button>
              </div>
            </div>

            {genStatus ? (
              <>
                <p className="text-xs text-gray-400 mb-4">{genStatus.todayKst} 기준 (KST)</p>
                <div className="space-y-3" data-testid="ai-gen-status-panel">
                  {([
                    { key: 'posts',    label: '글' },
                    { key: 'comments', label: '댓글' },
                    { key: 'replies',  label: '대댓글' },
                    { key: 'votes',    label: '투표' },
                    { key: 'likes',    label: '좋아요' },
                  ] as { key: keyof GenerationStatus['targets']; label: string }[]).map(({ key, label }) => {
                    const t = genStatus.targets[key];
                    const pct = t.percent;
                    const barColor = pct >= 100 ? 'bg-green-500' : pct >= 70 ? 'bg-[#5F8F76]' : 'bg-gray-400';
                    return (
                      <div key={key} data-testid={`ai-gen-status-${key}`}>
                        <div className="flex justify-between text-sm mb-1">
                          <span className="text-gray-700">{label}</span>
                          <span className="text-gray-500">
                            {t.done.toLocaleString()} / {t.target.toLocaleString()}
                            {t.target > 0 && <span className="ml-1 text-gray-400">({pct}%)</span>}
                          </span>
                        </div>
                        <div className="h-2 rounded-full bg-gray-100 overflow-hidden">
                          <div
                            className={`h-full rounded-full transition-all ${barColor}`}
                            style={{ width: `${Math.min(100, pct)}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
                {(genStatus.failures.failed > 0 || genStatus.failures.blocked > 0) && (
                  <p className="mt-3 text-xs text-gray-400">
                    실패 {genStatus.failures.failed}건 · 차단 {genStatus.failures.blocked}건 (오늘)
                  </p>
                )}
              </>
            ) : (
              <p className="text-sm text-gray-400" data-testid="ai-gen-status-empty">
                {statusLoading ? '불러오는 중...' : '새로고침을 눌러 현황을 조회하세요.'}
              </p>
            )}
          </section>
        </TabsContent>

        <TabsContent value="monitor" className="mt-6 space-y-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <ActionFeed />
            <PersonaPerformanceTable />
          </div>
          <HourlyDistributionChart />
        </TabsContent>

        <TabsContent value="api-settings" className="mt-6 space-y-4">
          <div className="p-3 bg-amber-50 border border-amber-200 rounded-md text-xs text-amber-800 space-y-1">
            <p className="font-semibold">🔑 Claude / Anthropic API 설정</p>
            <p>Anthropic API를 직접 호출하는 기능(수정 분석, API 모드 AI 유저 등)에 사용되는 설정입니다.</p>
            <p>키와 Base URL은 DB에 저장됩니다. Base URL 변경 시 llm-worker에 즉시 반영됩니다.</p>
          </div>
          <AnthropicBaseUrlPanel />
          <AnthropicApiKeyPanel />
        </TabsContent>
      </Tabs>
    </div>
  );
}
