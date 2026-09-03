import { api } from '@/lib/api/client';

// ── Types ─────────────────────────────────────────────────────────────────

/** 계획형 AI 사용자 생성 경로. API 키가 아닌 연결된 CLI 세션만 사용한다. */
export type ThreadPlanProvider = 'CLAUDE' | 'CODEX' | 'OFF';

export interface EstimateResult {
  callsPerDay: number;
  cliTokensTotal: number;
  cliPct: number;       // Max 5x 일일 한도 대비 %
  cliPeakPct: number;   // 저녁 피크 5h 윈도우 대비 %
  apiCostDay: number;   // API $/일
  apiCostMonth: number; // API $/월
  apiTokensTotal: number;
  warnings: string[];   // "DANGER:...", "WARN:...", "INFO:..."
}

export interface GenerationConfig {
  targetPosts: number;
  targetComments: number;
  targetReplies: number;
  targetVotes: number;
  targetLikes: number;
  autoComment: boolean;
  autoReply: boolean;
  updatedBy: string | null;
  updatedAt: string | null;
  ratioComment: number;
  ratioReply: number;
  ratioVote: number;
  ratioLike: number;
  estimate: EstimateResult;

  providerAiPostBundle: ThreadPlanProvider;
  providerHumanPostPlan: ThreadPlanProvider;
  providerHumanInteraction: ThreadPlanProvider;
  providerVoteLike: ThreadPlanProvider;
  scheduleExecutionPaused: boolean;
  aiUserKillSwitch: boolean;
  candidatePoolSize: number;
  humanBatchMaxPosts: number;
  humanBatchMaxInteractions: number;
  hrRespondersPerInteractionMax: number;
  hrDistinctPersonasMax: number;
  hrRepliesPerPersonaMax: number;
  hrCandidateRespondersMax: number;
  hrChunkSize: number;
  hrDelayMinutesMin: number;
  hrDelayMinutesMax: number;
  /** 구조화 LLM 타임아웃(ms). 저장 즉시 반영. 60000~900000 */
  bundleTimeoutMs: number;
  /** 새벽 배치 양면 비율 0~1 */
  nightlyPairedShare: number;
  nightlySlotFromHour: number;
  nightlySlotToHour: number;
  nightlySlotMinSpacingMinutes: number;
  /** 파생값 = hrDistinctPersonasMax × hrRepliesPerPersonaMax (서버 계산, 전송 불필요) */
  hrRepliesPerPostHumanMax: number;
}

export interface UpdateConfigRequest {
  targetPosts: number;
  targetComments: number;
  targetReplies: number;
  targetVotes: number;
  targetLikes: number;
  autoComment: boolean;
  autoReply: boolean;
  providerAiPostBundle: ThreadPlanProvider;
  providerHumanPostPlan: ThreadPlanProvider;
  providerHumanInteraction: ThreadPlanProvider;
  providerVoteLike: ThreadPlanProvider;
  scheduleExecutionPaused: boolean;
  aiUserKillSwitch: boolean;
  candidatePoolSize: number;
  humanBatchMaxPosts: number;
  humanBatchMaxInteractions: number;
  hrRespondersPerInteractionMax: number;
  hrDistinctPersonasMax: number;
  hrRepliesPerPersonaMax: number;
  hrCandidateRespondersMax: number;
  hrChunkSize: number;
  hrDelayMinutesMin: number;
  hrDelayMinutesMax: number;
  bundleTimeoutMs: number;
  nightlyPairedShare: number;
  nightlySlotFromHour: number;
  nightlySlotToHour: number;
  nightlySlotMinSpacingMinutes: number;
}

// ── API calls ────────────────────────────────────────────────────────────

export async function getGenerationConfig(): Promise<GenerationConfig> {
  const res = await api.get('/api/admin/ai-user/generation-config');
  return res.data;
}

export async function updateGenerationConfig(req: UpdateConfigRequest): Promise<GenerationConfig> {
  const res = await api.put('/api/admin/ai-user/generation-config', req);
  return res.data;
}

export async function killAllBackends(): Promise<{ status: string; message: string; killedAt: string }> {
  const res = await api.post('/api/admin/ai-user/kill');
  return res.data;
}

// ── Generation Status ────────────────────────────────────────────────────

export interface TypeProgress {
  done: number;
  target: number;
  percent: number;
}

export interface GenerationStatus {
  todayKst: string;
  targets: {
    posts: TypeProgress;
    comments: TypeProgress;
    replies: TypeProgress;
    votes: TypeProgress;
    likes: TypeProgress;
  };
  failures: {
    failed: number;
    blocked: number;
  };
}

export async function getGenerationStatus(): Promise<GenerationStatus> {
  const res = await api.get('/api/admin/ai-user/generation-status');
  return res.data;
}

// ── Action Feed ──────────────────────────────────────────────────────────

export interface ActionFeedItem {
  id: number;
  personaId: string;
  personaNickname: string | null;
  personaTier: string | null;
  action: string; // LIKE/VOTE/COMMENT/REPLY/POST
  status: string; // POSTED/FAILED/BLOCKED/GENERATING/PLANNED
  targetType: string | null;
  targetId: string | null;
  detail: string | null; // raw JSON string
  failed: boolean;
  blocked: boolean;
  createdAt: string;
}

export interface ActionFeedResponse {
  feeds: ActionFeedItem[];
  total: number;
}

export async function getActionFeed(
  limit?: number,
  status?: string,
  actionType?: string
): Promise<ActionFeedResponse> {
  const params = new URLSearchParams();
  if (limit !== undefined) params.append('limit', String(limit));
  if (status) params.append('status', status);
  if (actionType) params.append('actionType', actionType);
  const res = await api.get(`/api/admin/ai-user/action-feed?${params.toString()}`);
  return res.data;
}

// ── Persona Performance ──────────────────────────────────────────────────

export interface PersonaPerformanceDto {
  personaId: string;
  nickname: string | null;
  tier: string | null;
  active: boolean;
  actionsCompleted: number;
  failed: number;
  blocked: number;
  failureRate: number;
  realUserReactions: number;
}

export async function getPersonaPerformance(range?: '24h' | '7d'): Promise<PersonaPerformanceDto[]> {
  const params = new URLSearchParams();
  if (range) params.append('range', range);
  const res = await api.get(`/api/admin/ai-user/persona-performance?${params.toString()}`);
  return res.data;
}

// ── Hourly Distribution ──────────────────────────────────────────────────

export interface HourSlot {
  hour: number;
  actual: number;
  byType: Record<string, number>;
}

export interface HourlyDistributionResponse {
  hours: HourSlot[];
}

export async function getHourlyDistribution(hours?: number): Promise<HourlyDistributionResponse> {
  const params = new URLSearchParams();
  if (hours !== undefined) params.append('hours', String(hours));
  const res = await api.get(`/api/admin/ai-user/hourly-distribution?${params.toString()}`);
  return res.data;
}

// ── Effective Gates (env/yml/DB/LLM 게이트 해석 결과) ─────────────────────

/** 개별 게이트 1건. `value`는 boolean(on/off) 또는 provider 문자열('CLAUDE'/'OFF' 등)이다. */
export interface EffectiveGate {
  name: string;
  source: 'env' | 'yml' | 'db';
  value: boolean | string;
  blocks: string;
}

export interface EffectiveGates {
  generationAllowed: boolean;
  publishingAllowed: boolean;
  reasons: string[];
  gates: EffectiveGate[];
  /** orchestrator가 stale 스냅샷을 감지했을 때만 채워짐. 아직 미배선. */
  stale?: boolean;
}

export async function getEffectiveGates(): Promise<EffectiveGates> {
  const res = await api.get('/api/admin/ai-user/effective-gates');
  return res.data;
}
