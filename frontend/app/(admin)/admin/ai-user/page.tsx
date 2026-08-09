'use client';

import { useState, useEffect, useCallback } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { AdminSection } from '@/components/admin/AdminSection';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { ActionFeed } from '@/components/admin/ai-user/ActionFeed';
import { PersonaPerformanceTable } from '@/components/admin/ai-user/PersonaPerformanceTable';
import { HourlyDistributionChart } from '@/components/admin/ai-user/HourlyDistributionChart';
import {
  getGenerationConfig,
  updateGenerationConfig,
  killAllBackends,
  getGenerationStatus,
  type GenerationConfig,
  type ThreadPlanProvider,
  type UpdateConfigRequest,
  type EstimateResult,
  type GenerationStatus,
} from '@/lib/api/admin/ai-user';
import {
  Cpu, Zap, Power, Save, AlertTriangle, AlertCircle, Info, RefreshCw,
} from 'lucide-react';
import { formatDate } from '@/lib/utils/adminFormat';

// ── §11.5 클라이언트 측 추정 상수 (Claude provider만) ────────────────────
// 실측 기준 (ClaudeApiInvoker 로그 avg): input ~4600, output ~100
const PERCALL = {
  post:    { in: 4_800, out: 300  },  // self-critique 포함 약간 높음
  comment: { in: 4_600, out: 100  },
  reply:   { in: 4_300, out: 80   },
};
const MAX5X_DAILY  = 2_100_000;  // Max 5x = Pro(420K) × 5
const MAX5X_WINDOW = 440_000;    // Max 5x = Pro(88K) × 5
const PEAK_SHARE   = 0.208;      // 균등분포 기준 5h/24h

function computeEstimateForClaudeProviders(
  posts: number, comments: number, replies: number,
  pAiPostBundle: ThreadPlanProvider,
  pHumanPostPlan: ThreadPlanProvider,
  pHumanInteraction: ThreadPlanProvider,
): { claudeTokens: number; claudeCalls: number; cliPct: number; cliPeakPct: number } {
  let claudeTokens = 0, claudeCalls = 0;

  // 글: 새벽 배치가 target_posts만큼 생성 (주간 provider OFF여도 CLAUDE 경로로 추정)
  if (posts > 0 && (pAiPostBundle === 'CLAUDE' || pAiPostBundle === 'OFF' || !pAiPostBundle)) {
    claudeCalls += posts;
    claudeTokens += posts * (PERCALL.post.in + PERCALL.post.out);
  }
  // 사람 글→AI 댓글 / 사람 댓글 답글은 해당 provider가 CLAUDE일 때만
  if (pHumanPostPlan === 'CLAUDE' && comments > 0) {
    claudeCalls += comments;
    claudeTokens += comments * (PERCALL.comment.in + PERCALL.comment.out);
  }
  if (pHumanInteraction === 'CLAUDE') {
    if (comments > 0) {
      claudeCalls += comments;
      claudeTokens += comments * (PERCALL.comment.in + PERCALL.comment.out);
    }
    if (replies > 0) {
      claudeCalls += replies;
      claudeTokens += replies * (PERCALL.reply.in + PERCALL.reply.out);
    }
  }

  const cliPct = (claudeTokens / MAX5X_DAILY) * 100;
  const cliPeakPct = (claudeTokens * PEAK_SHARE / MAX5X_WINDOW) * 100;

  return { claudeTokens, claudeCalls, cliPct, cliPeakPct };
}

function computeEstimate(
  posts: number, comments: number, replies: number,
  pAiPostBundle: ThreadPlanProvider,
  pHumanPostPlan: ThreadPlanProvider,
  pHumanInteraction: ThreadPlanProvider,
  pVoteLike: ThreadPlanProvider,
): EstimateResult {
  const { claudeTokens, claudeCalls, cliPct, cliPeakPct } = computeEstimateForClaudeProviders(
    posts, comments, replies,
    pAiPostBundle, pHumanPostPlan, pHumanInteraction
  );

  const warnings: string[] = [];
  const totalCalls = claudeCalls + (pVoteLike === 'CLAUDE' ? posts : 0); // votes+likes 무시

  if (totalCalls === 0) {
    warnings.push('INFO:생성 없음 — 모든 provider가 OFF 또는 목표량 0');
  } else {
    if (cliPct > 100)      warnings.push('DANGER:Claude 경로가 Max 5x 일일 한도 초과 — 종일 throttle 위험');
    else if (cliPct > 80)  warnings.push('WARN:Claude 경로가 Max 5x 한도 80% 초과 — 개발 quota 공유 주의');
    if (cliPeakPct > 100)  warnings.push('WARN:저녁 피크 5h 윈도우 초과 — 피크 시간대 throttle 위험');
  }

  return {
    callsPerDay: totalCalls,
    cliTokensTotal: claudeTokens,
    cliPct,
    cliPeakPct,
    apiCostDay: 0,
    apiCostMonth: 0,
    apiTokensTotal: 0,
    warnings,
  };
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

// ── 계획형 생성 제공자 선택 (연결된 CLI 세션만 사용) ─────────────────────
function ThreadPlanProviderSelector({
  label, description, value, onChange,
}: {
  label: string;
  description: string;
  value: ThreadPlanProvider;
  onChange: (v: ThreadPlanProvider) => void;
}) {
  const badge = value === 'CLAUDE' ? 'bg-orange-100 text-orange-700 border-orange-200' :
                value === 'CODEX'  ? 'bg-blue-100 text-blue-700 border-blue-200' :
                                     'bg-gray-100 text-gray-500 border-gray-200';
  return (
    <div className="py-3 border-b last:border-b-0">
      <div className="flex items-start gap-4">
        <div className="min-w-0 flex-1">
          <div className="text-sm font-medium text-gray-700">{label}</div>
          <p className="mt-0.5 text-xs text-gray-400">{description}</p>
        </div>
        <span className={`text-[11px] font-medium border rounded px-2 py-0.5 ${badge}`}>{value}</span>
      </div>
      <div className="mt-2 flex items-center gap-5">
        {(['CLAUDE', 'CODEX', 'OFF'] as ThreadPlanProvider[]).map(opt => (
          <label key={opt} className="flex items-center gap-1.5 cursor-pointer text-sm">
            <input
              type="radio"
              name={`thread-provider-${label}`}
              value={opt}
              checked={value === opt}
              onChange={() => onChange(opt)}
              className="accent-blue-600"
              data-testid={`ai-thread-provider-${label}-${opt}`}
            />
            <span className={opt === 'OFF' ? 'text-gray-400' : ''}>{opt}</span>
          </label>
        ))}
      </div>
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
  const [ratios,   setRatios]   = useState({ comment: 7.6, reply: 4.4, vote: 6.5, like: 15.7 });

  // 계획형 AI 사용자 실행 (PLAN 전용): 4가지 provider로 제어
  const [providerAiPostBundle, setProviderAiPostBundle] = useState<ThreadPlanProvider>('OFF');
  const [providerHumanPostPlan, setProviderHumanPostPlan] = useState<ThreadPlanProvider>('OFF');
  const [providerHumanInteraction, setProviderHumanInteraction] = useState<ThreadPlanProvider>('OFF');
  const [providerVoteLike, setProviderVoteLike] = useState<ThreadPlanProvider>('OFF');
  const [scheduleExecutionPaused, setScheduleExecutionPaused] = useState(false);
  const [aiUserKillSwitch, setAiUserKillSwitch] = useState(false);
  const [candidatePoolSize, setCandidatePoolSize] = useState(24);
  const [humanBatchMaxPosts, setHumanBatchMaxPosts] = useState(10);
  const [humanBatchMaxInteractions, setHumanBatchMaxInteractions] = useState(50);
  // 댓글 생성량 설정 (SSOT: ai_user_generation_config)
  const [hrRespondersPerInteractionMax, setHrRespondersPerInteractionMax] = useState(3);
  const [hrDistinctPersonasMax, setHrDistinctPersonasMax] = useState(3);
  const [hrRepliesPerPersonaMax, setHrRepliesPerPersonaMax] = useState(5);
  const [hrCandidateRespondersMax, setHrCandidateRespondersMax] = useState(8);
  const [hrChunkSize, setHrChunkSize] = useState(20);
  const [hrDelayMinutesMin, setHrDelayMinutesMin] = useState(1);
  const [hrDelayMinutesMax, setHrDelayMinutesMax] = useState(30);
  const [bundleTimeoutMs, setBundleTimeoutMs] = useState(600_000);
  const [nightlyPairedShare, setNightlyPairedShare] = useState(0.2);
  const [nightlySlotFromHour, setNightlySlotFromHour] = useState(8);
  const [nightlySlotToHour, setNightlySlotToHour] = useState(22);
  const [nightlySlotMinSpacingMinutes, setNightlySlotMinSpacingMinutes] = useState(45);

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
    setRatios({ comment: cfg.ratioComment, reply: cfg.ratioReply, vote: cfg.ratioVote, like: cfg.ratioLike });
    setServerEstimate(cfg.estimate);
    setProviderAiPostBundle(cfg.providerAiPostBundle ?? 'OFF');
    setProviderHumanPostPlan(cfg.providerHumanPostPlan ?? 'OFF');
    setProviderHumanInteraction(cfg.providerHumanInteraction ?? 'OFF');
    setProviderVoteLike(cfg.providerVoteLike ?? 'OFF');
    setScheduleExecutionPaused(cfg.scheduleExecutionPaused ?? false);
    setAiUserKillSwitch(cfg.aiUserKillSwitch ?? false);
    setCandidatePoolSize(cfg.candidatePoolSize ?? 24);
    setHumanBatchMaxPosts(cfg.humanBatchMaxPosts ?? 10);
    setHumanBatchMaxInteractions(cfg.humanBatchMaxInteractions ?? 50);
    setHrRespondersPerInteractionMax(cfg.hrRespondersPerInteractionMax ?? 3);
    setHrDistinctPersonasMax(cfg.hrDistinctPersonasMax ?? 3);
    setHrRepliesPerPersonaMax(cfg.hrRepliesPerPersonaMax ?? 5);
    setHrCandidateRespondersMax(cfg.hrCandidateRespondersMax ?? 8);
    setHrChunkSize(cfg.hrChunkSize ?? 20);
    setHrDelayMinutesMin(cfg.hrDelayMinutesMin ?? 1);
    setHrDelayMinutesMax(cfg.hrDelayMinutesMax ?? 30);
    setBundleTimeoutMs(cfg.bundleTimeoutMs ?? 600_000);
    setNightlyPairedShare(cfg.nightlyPairedShare ?? 0.2);
    setNightlySlotFromHour(cfg.nightlySlotFromHour ?? 8);
    setNightlySlotToHour(cfg.nightlySlotToHour ?? 22);
    setNightlySlotMinSpacingMinutes(cfg.nightlySlotMinSpacingMinutes ?? 45);
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
  const liveEstimate = computeEstimate(
    posts, comments, replies,
    providerAiPostBundle, providerHumanPostPlan, providerHumanInteraction, providerVoteLike
  );
  const est = serverEstimate ?? liveEstimate;

  // ── 저장 ─────────────────────────────────────────────────────────
  const handleSave = async () => {
    setSaving(true); setError(''); setSavedMsg('');
    const req: UpdateConfigRequest = {
      targetPosts: posts, targetComments: comments, targetReplies: replies,
      targetVotes: votes, targetLikes: likes,
      autoComment, autoReply,
      providerAiPostBundle,
      providerHumanPostPlan,
      providerHumanInteraction,
      providerVoteLike,
      scheduleExecutionPaused,
      aiUserKillSwitch,
      candidatePoolSize,
      humanBatchMaxPosts,
      humanBatchMaxInteractions,
      hrRespondersPerInteractionMax,
      hrDistinctPersonasMax,
      hrRepliesPerPersonaMax,
      hrCandidateRespondersMax,
      hrChunkSize,
      hrDelayMinutesMin,
      hrDelayMinutesMax,
      bundleTimeoutMs,
      nightlyPairedShare,
      nightlySlotFromHour,
      nightlySlotToHour,
      nightlySlotMinSpacingMinutes,
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

  const allOff = aiUserKillSwitch || (
    providerAiPostBundle === 'OFF'
    && providerHumanPostPlan === 'OFF'
    && providerHumanInteraction === 'OFF'
    && providerVoteLike === 'OFF'
  );

  return (
    <div className="space-y-6 max-w-6xl">

      <AdminPageHeader
        title="AI 생성 관제"
        description="일일 생성량·백엔드를 실시간 조정하고 토큰·비용 추정을 확인합니다."
        action={
          <div className="flex items-center gap-2">
            <Badge className={allOff ? 'bg-gray-100 text-gray-500 border-gray-200' : 'bg-emerald-100 text-emerald-700 border-emerald-200'}>
              {allOff ? '전체 OFF' : '활성'}
            </Badge>
            <span className="text-sm text-gray-400">오늘 {est.callsPerDay} 콜 예상</span>
          </div>
        }
      />

      {/* 에러 / 성공 메시지 */}
      {error    && <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {savedMsg && <div className="rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{savedMsg}</div>}

      {/* 탭 구조 */}
      <Tabs defaultValue="settings" className="w-full">
        <TabsList>
          <TabsTrigger value="settings">생성 설정</TabsTrigger>
          <TabsTrigger value="monitor">실시간 관제</TabsTrigger>
        </TabsList>

        <TabsContent value="settings" className="mt-6">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

        {/* ── 왼쪽 2/3: 설정 영역 ─────────────────────────────────── */}
        <div className="lg:col-span-2 space-y-6">

          {/* 일일 생성 목표량 */}
          <AdminSection title="일일 생성 목표량">
            <div className="space-y-5 px-1">
              <div className="rounded-md border border-amber-100 bg-amber-50 px-3 py-2.5 text-xs text-amber-900">
                <strong>글 (POST)</strong> 목표량은 새벽 배치(03:05 KST)가 실제로 생성하는 개수입니다.
                저장 즉시 DB에 반영되며, 다음 새벽 배치부터 적용됩니다.
              </div>
              <SliderRow
                label="글 (POST)"
                value={posts} min={0} max={100}
                onChange={handlePostsChange}
                badge="새벽 배치 생성 개수 = 이 값"
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

          {/* 댓글 생성량 설정 — human-reply 규칙의 SSOT */}
          <AdminSection title="댓글 생성량 설정">
            <div className="px-1 space-y-4">
              <div className="rounded-md border border-blue-100 bg-blue-50 px-3 py-2.5 text-xs text-blue-800">
                사람이 남긴 댓글에 AI 유저가 답글을 다는 규칙입니다. 여기 값이 유일한 기준이며,
                30분 주기 배치가 저장 즉시 반영합니다.
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="space-y-1">
                  <Label htmlFor="hr-responders-max" className="text-xs text-gray-500">댓글 1건당 답글 수</Label>
                  <Input id="hr-responders-max" type="number" min={0} max={5} value={hrRespondersPerInteractionMax}
                    onChange={e => setHrRespondersPerInteractionMax(Math.max(0, Math.min(5, Number(e.target.value))))} className="h-8" />
                  <div className="text-[11px] text-gray-400">사람 댓글 하나에 붙는 AI 답글 상한. 0건도 정상입니다.</div>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="hr-distinct-personas" className="text-xs text-gray-500">대화 참여 AI 유저 수</Label>
                  <Input id="hr-distinct-personas" type="number" min={1} max={10} value={hrDistinctPersonasMax}
                    onChange={e => setHrDistinctPersonasMax(Math.max(1, Math.min(10, Number(e.target.value))))} className="h-8" />
                  <div className="text-[11px] text-gray-400">같은 글·같은 사람과의 대화에 들어올 수 있는 서로 다른 AI 유저 수.</div>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="hr-per-persona" className="text-xs text-gray-500">AI 유저 1명당 답글 수</Label>
                  <Input id="hr-per-persona" type="number" min={1} max={10} value={hrRepliesPerPersonaMax}
                    onChange={e => setHrRepliesPerPersonaMax(Math.max(1, Math.min(10, Number(e.target.value))))} className="h-8" />
                  <div className="text-[11px] text-gray-400">한 AI 유저가 그 대화에서 답글을 다는 횟수 상한.</div>
                </div>
              </div>

              <div className="rounded-md border bg-gray-50 px-3 py-2.5">
                <div className="text-xs text-gray-500">대화 총상한 (자동 계산)</div>
                <div className="text-lg font-mono font-semibold text-gray-700">
                  {hrDistinctPersonasMax} × {hrRepliesPerPersonaMax} = {hrDistinctPersonasMax * hrRepliesPerPersonaMax}개
                </div>
                <div className="text-[11px] text-gray-400 mt-1">
                  한 사람이 한 글에서 받는 AI 답글 총합입니다. 위 두 값에서 자동으로 계산되므로 따로 저장하지 않습니다.
                  다른 사람과의 대화는 이 한도를 공유하지 않고 각자 새로 시작합니다.
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 border-t pt-4">
                <div className="space-y-1">
                  <Label htmlFor="hr-candidate-max" className="text-xs text-gray-500">글당 답글 후보 AI 유저 수</Label>
                  <Input id="hr-candidate-max" type="number" min={1} max={50} value={hrCandidateRespondersMax}
                    onChange={e => setHrCandidateRespondersMax(Math.max(1, Math.min(50, Number(e.target.value))))} className="h-8" />
                  <div className="text-[11px] text-gray-400">그 글에 관심 있는 AI 유저 중 몇 명까지 후보로 올릴지. 이 안에서만 답글이 나옵니다.</div>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="hr-chunk-size" className="text-xs text-gray-500">호출 1회당 처리 댓글 수</Label>
                  <Input id="hr-chunk-size" type="number" min={1} max={50} value={hrChunkSize}
                    onChange={e => setHrChunkSize(Math.max(1, Math.min(50, Number(e.target.value))))} className="h-8" />
                  <div className="text-[11px] text-gray-400">이 수를 넘으면 그만큼 호출을 나눠 보냅니다. 클수록 호출 수는 줄고 한 번에 오래 걸립니다.</div>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1">
                  <Label htmlFor="hr-delay-min" className="text-xs text-gray-500">답글 게시 지연 최소(분)</Label>
                  <Input id="hr-delay-min" type="number" min={1} max={720} value={hrDelayMinutesMin}
                    onChange={e => setHrDelayMinutesMin(Math.max(1, Math.min(720, Number(e.target.value))))} className="h-8" />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="hr-delay-max" className="text-xs text-gray-500">답글 게시 지연 최대(분)</Label>
                  <Input id="hr-delay-max" type="number" min={1} max={720} value={hrDelayMinutesMax}
                    onChange={e => setHrDelayMinutesMax(Math.max(1, Math.min(720, Number(e.target.value))))} className="h-8" />
                </div>
              </div>
              {hrDelayMinutesMin > hrDelayMinutesMax && (
                <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                  최소가 최대보다 큽니다. 저장하면 두 값을 바꿔서 기록합니다.
                </div>
              )}
              <div className="text-[11px] text-gray-400">
                답글은 배치 실행 시점에서 이 범위 안의 임의 시각에 올라갑니다. 사람 댓글 직후 동시에 몰리지 않게 하는 값입니다.
              </div>
            </div>
          </AdminSection>

          {/* 계획형 생성 경로 */}
          <AdminSection title="계획형 AI 사용자 실행">
            <div className="px-1 space-y-4">
              <div className="rounded-md border border-blue-100 bg-blue-50 px-3 py-2.5 text-xs text-blue-800 space-y-1.5">
                <p>
                  게시글 하나당 글·댓글·대댓글 후보를 한 번에 생성하고, 예약 실행기가 시간에 맞춰 게시합니다.
                  API 키 없이 연결된 Claude Code / Codex CLI 세션만 사용합니다.
                </p>
                <p>
                  아래 값은 <strong>관리자 저장값이 SSOT</strong>입니다. 새벽 배치는 작업 중에만 잠깐 CLAUDE로
                  켠 뒤 <strong>저장해 둔 값으로 복원</strong>하며, OFF로 강제하지 않습니다.
                </p>
              </div>

              <div className="space-y-3" data-testid="ai-plan-provider-controls">
                <ThreadPlanProviderSelector
                  label="AI 글·댓글 묶음 생성"
                  description="새 AI 글·댓글 묶음 LLM job. OFF면 낮에는 생성하지 않고, 새벽 배치가 잠깐 켠 뒤 이 값으로 돌아갑니다."
                  value={providerAiPostBundle}
                  onChange={setProviderAiPostBundle}
                />
                <ThreadPlanProviderSelector
                  label="사람 글 → AI 댓글"
                  description="사람이 글을 쓰면 AI가 비동기로 댓글 후보를 만듭니다. CLAUDE/CODEX로 두면 상시 동작합니다."
                  value={providerHumanPostPlan}
                  onChange={setProviderHumanPostPlan}
                />
                <ThreadPlanProviderSelector
                  label="사람 댓글 확인·답글"
                  description="30분마다 사람 댓글·대댓글을 묶어 AI 답글을 만듭니다(최대 20개/호출). CLAUDE로 두면 하루 종일 상시 ON입니다."
                  value={providerHumanInteraction}
                  onChange={setProviderHumanInteraction}
                />
                <ThreadPlanProviderSelector
                  label="AI 투표·좋아요 생성"
                  description="AI가 게시글 공감 투표와 좋아요를 생성합니다. 실제 여론에 영향을 주기보다는 커뮤니티가 비어 보이지 않게 하는 역할이며, 사람 투표가 있으면 공감 비율은 사람 투표가 우선합니다."
                  value={providerVoteLike}
                  onChange={setProviderVoteLike}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 border-t pt-4">
                <div className="space-y-1">
                  <Label htmlFor="candidate-pool-size" className="text-xs text-gray-500">게시글당 후보 수</Label>
                  <Input id="candidate-pool-size" type="number" min={8} max={30} value={candidatePoolSize}
                    onChange={e => setCandidatePoolSize(Math.max(8, Math.min(30, Number(e.target.value))))} className="h-8" />
                  <p className="text-[11px] text-gray-400">예약 후보 풀 (8–30)</p>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="human-batch-posts" className="text-xs text-gray-500">배치당 글 수</Label>
                  <Input id="human-batch-posts" type="number" min={1} max={10} value={humanBatchMaxPosts}
                    onChange={e => setHumanBatchMaxPosts(Math.max(1, Math.min(10, Number(e.target.value))))} className="h-8" />
                  <p className="text-[11px] text-gray-400">사람 댓글 답글 배치 (최대 10)</p>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="human-batch-interactions" className="text-xs text-gray-500">배치당 상호작용 수</Label>
                  <Input id="human-batch-interactions" type="number" min={1} max={50} value={humanBatchMaxInteractions}
                    onChange={e => setHumanBatchMaxInteractions(Math.max(1, Math.min(50, Number(e.target.value))))} className="h-8" />
                  <p className="text-[11px] text-gray-400">사람 댓글·대댓글 합계 (최대 50)</p>
                </div>
              </div>

              <div className="border-t pt-4 space-y-4">
                <div className="text-sm font-medium text-gray-700">LLM 타임아웃 · 새벽 배치</div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-1">
                    <Label htmlFor="bundle-timeout" className="text-xs text-gray-500">구조화 생성 타임아웃(초)</Label>
                    <Input
                      id="bundle-timeout"
                      type="number"
                      min={60}
                      max={900}
                      value={Math.round(bundleTimeoutMs / 1000)}
                      onChange={e => {
                        const sec = Math.max(60, Math.min(900, Number(e.target.value) || 600));
                        setBundleTimeoutMs(sec * 1000);
                      }}
                      className="h-8"
                    />
                    <p className="text-[11px] text-gray-400">
                      solo / paired / 사람답글 LLM 공통. 저장 즉시 다음 호출부터 적용 (60–900초).
                    </p>
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="nightly-paired-share" className="text-xs text-gray-500">양면(paired) 비율</Label>
                    <Input
                      id="nightly-paired-share"
                      type="number"
                      min={0}
                      max={1}
                      step={0.05}
                      value={nightlyPairedShare}
                      onChange={e => setNightlyPairedShare(Math.max(0, Math.min(1, Number(e.target.value) || 0)))}
                      className="h-8"
                    />
                    <p className="text-[11px] text-gray-400">
                      ceil(글목표 × 비율). 예: 10×0.2 → paired 2 + solo 8
                    </p>
                  </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                  <div className="space-y-1">
                    <Label htmlFor="slot-from" className="text-xs text-gray-500">슬롯 시작(KST시)</Label>
                    <Input id="slot-from" type="number" min={0} max={23} value={nightlySlotFromHour}
                      onChange={e => setNightlySlotFromHour(Math.max(0, Math.min(23, Number(e.target.value) || 0)))} className="h-8" />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="slot-to" className="text-xs text-gray-500">슬롯 끝(KST시)</Label>
                    <Input id="slot-to" type="number" min={1} max={24} value={nightlySlotToHour}
                      onChange={e => setNightlySlotToHour(Math.max(1, Math.min(24, Number(e.target.value) || 22)))} className="h-8" />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="slot-spacing" className="text-xs text-gray-500">슬롯 최소 간격(분)</Label>
                    <Input id="slot-spacing" type="number" min={15} max={180} value={nightlySlotMinSpacingMinutes}
                      onChange={e => setNightlySlotMinSpacingMinutes(Math.max(15, Math.min(180, Number(e.target.value) || 45)))} className="h-8" />
                  </div>
                </div>
              </div>

              <div className="border-t pt-4 space-y-3">
                <label className="flex items-start gap-2 cursor-pointer text-sm">
                  <Checkbox checked={scheduleExecutionPaused} onCheckedChange={v => setScheduleExecutionPaused(Boolean(v))} />
                  <span><span className="font-medium">예약 실행 일시 정지</span><span className="block text-xs text-gray-400 mt-0.5">이미 생성된 계획은 보존하고, 게시만 멈춥니다.</span></span>
                </label>
                <label className="flex items-start gap-2 cursor-pointer text-sm">
                  <Checkbox checked={aiUserKillSwitch} onCheckedChange={v => setAiUserKillSwitch(Boolean(v))} />
                  <span><span className="font-medium text-red-700">전체 킬 스위치</span><span className="block text-xs text-gray-400 mt-0.5">새 계획 생성과 예약 실행을 모두 차단합니다. 저장 후 적용됩니다.</span></span>
                </label>
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

                {/* Claude 경로 */}
                <div className="space-y-2">
                  <div className="text-xs font-medium text-orange-600 flex items-center gap-1">
                    <span className="h-2 w-2 rounded-full bg-orange-500 inline-block" />
                    Claude 경로 (Max 5x)
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

                {/* Codex 경로 및 기타 */}
                {(providerAiPostBundle === 'CODEX' || providerHumanPostPlan === 'CODEX' ||
                  providerHumanInteraction === 'CODEX' || providerVoteLike === 'CODEX') && (
                  <div className="space-y-1.5">
                    <div className="text-xs font-medium text-blue-600 flex items-center gap-1">
                      <span className="h-2 w-2 rounded-full bg-blue-500 inline-block" />
                      Codex 경로
                    </div>
                    <p className="text-[11px] text-gray-500">
                      비용 추정 미지원 · Codex 워커 활용 (일일 호출 수는 서버 보고)
                    </p>
                  </div>
                )}

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
      </Tabs>
    </div>
  );
}
