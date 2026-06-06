import { api } from '@/lib/api/client';

// ── Types ─────────────────────────────────────────────────────────────────

export type Backend = 'CLI' | 'API' | 'OFF';

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
  backendPost: Backend;
  backendComment: Backend;
  backendReply: Backend;
  promptCaching: boolean;
  dailyTokenBudget: number | null;
  updatedBy: string | null;
  updatedAt: string | null;
  ratioComment: number;
  ratioReply: number;
  ratioVote: number;
  ratioLike: number;
  estimate: EstimateResult;
}

export interface UpdateConfigRequest {
  targetPosts: number;
  targetComments: number;
  targetReplies: number;
  targetVotes: number;
  targetLikes: number;
  autoComment: boolean;
  autoReply: boolean;
  backendPost: Backend;
  backendComment: Backend;
  backendReply: Backend;
  promptCaching: boolean;
  dailyTokenBudget: number | null;
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
