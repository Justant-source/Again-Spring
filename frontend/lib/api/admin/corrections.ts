import { api } from '@/lib/api/client';
import type { PageResponse } from '../admin';

// ===== AI 첨삭 학습 Types =====

export interface AnalyzeRequest {
  targetType: 'POST' | 'COMMENT';
  targetId: string;
  correctedText: string;
}

export interface AnalyzeResponse {
  personaId: string;
  originalText: string;
  suggestedCaution: string | null;
  suggestedGlobalRules: string[];
}

export interface CommitRequest {
  targetType: 'POST' | 'COMMENT';
  targetId: string;
  correctedText: string;
  personaCaution: string | null;
  globalRules: string[];
  applyLive: boolean;
}

export interface CommitResponse {
  correctionId: number;
  appliedLive: boolean;
  rulesCreated: number;
  cautionApplied: boolean;
}

// ===== 전역 금지 규칙 Types =====

export interface AiGlobalRule {
  id: number;
  ruleText: string;
  scope: 'POST' | 'COMMENT' | 'ALL';
  sourceCorrectionId: number | null;
  active: boolean;
  createdBy: string;
  createdAt: string;
}

// ===== 페르소나 주의사항 Types =====

export interface AiCaution {
  id: number;
  personaId: string;
  targetType: string;
  targetId: string;
  originalText: string;
  correctedText: string;
  personaCaution: string | null;
  adminId: string;
  appliedLive: boolean;
  pushedToBank: boolean;
  createdAt: string;
}

// ===== 첨삭 분석·확정 API =====

/** 단계 A: 원본↔수정본 LLM 분석 (DB 저장 없음) */
export async function analyzeCorrection(req: AnalyzeRequest): Promise<AnalyzeResponse> {
  const res = await api.post<AnalyzeResponse>(
    '/api/admin/content/corrections/analyze',
    req
  );
  return res.data;
}

/** 단계 B: 관리자 확인 후 확정 저장 */
export async function commitCorrection(req: CommitRequest): Promise<CommitResponse> {
  const res = await api.post<CommitResponse>(
    '/api/admin/content/corrections/commit',
    req
  );
  return res.data;
}

// ===== 전역 금지 규칙 관리 API =====

export async function listGlobalRules(params: {
  page?: number;
  size?: number;
  active?: boolean;
}): Promise<PageResponse<AiGlobalRule>> {
  const queryParams: Record<string, any> = {
    page: params.page ?? 0,
    size: params.size ?? 20,
  };
  if (params.active !== undefined) queryParams.active = params.active;
  const res = await api.get<PageResponse<AiGlobalRule>>('/api/admin/ai-rules/global', {
    params: queryParams,
  });
  return res.data;
}

export async function createGlobalRule(ruleText: string, scope: string = 'ALL'): Promise<AiGlobalRule> {
  const res = await api.post<AiGlobalRule>('/api/admin/ai-rules/global', { ruleText, scope });
  return res.data;
}

export async function toggleGlobalRule(id: number, active: boolean): Promise<AiGlobalRule> {
  const res = await api.patch<AiGlobalRule>(`/api/admin/ai-rules/global/${id}`, { active });
  return res.data;
}

export async function deleteGlobalRule(id: number): Promise<void> {
  await api.delete(`/api/admin/ai-rules/global/${id}`);
}

// ===== 페르소나 주의사항 관리 API =====

export async function listCautions(params: {
  page?: number;
  size?: number;
  personaId?: string;
}): Promise<PageResponse<AiCaution>> {
  const queryParams: Record<string, any> = {
    page: params.page ?? 0,
    size: params.size ?? 20,
  };
  if (params.personaId) queryParams.personaId = params.personaId;
  const res = await api.get<PageResponse<AiCaution>>('/api/admin/ai-rules/cautions', {
    params: queryParams,
  });
  return res.data;
}

export async function toggleCaution(corrId: number, active: boolean): Promise<void> {
  await api.patch(`/api/admin/ai-rules/cautions/${corrId}`, { active });
}

export async function deleteCaution(corrId: number): Promise<void> {
  await api.delete(`/api/admin/ai-rules/cautions/${corrId}`);
}
