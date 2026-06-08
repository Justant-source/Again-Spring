import { api } from '@/lib/api/client';
import type { PageResponse } from '../admin';

// ===== 즉시 저장 (LLM 없음) Types =====

export interface SaveCorrectionRequest {
  targetType: 'POST' | 'COMMENT';
  targetId: string;
  correctedText: string;
  applyLive: boolean;
  /** 관리자가 첨삭 시 남긴 수정 의도·방향 (선택) */
  adminOpinion?: string | null;
}

export interface SaveCorrectionResponse {
  correctionId: number;
  appliedLive: boolean;
}

export interface BatchAnalyzeResponse {
  jobId: string;
  queued: number;
  message: string;
}

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

// ===== 첨삭 이력 Types =====

export interface AiCorrectionHistory {
  id: number;
  targetType: 'POST' | 'COMMENT';
  targetId: string;
  personaId: string;
  category: string | null;
  originalText: string;
  correctedText: string;
  /** 관리자가 첨삭 시 남긴 수정 의도·방향 */
  adminOpinion: string | null;
  personaCaution: string | null;
  adminId: string;
  /** PENDING | PROCESSED | SKIPPED */
  status: 'PENDING' | 'PROCESSED' | 'SKIPPED';
  appliedLive: boolean;
  pushedToBank: boolean;
  createdAt: string;
}

export interface ApplyHistoryRequest {
  /** "PERSONA" | "GLOBAL" | "BOTH" */
  scope: 'PERSONA' | 'GLOBAL' | 'BOTH';
  personaCaution: string | null;
  globalRules: string[];
  pushToBank: boolean;
}

export interface ApplyHistoryResponse {
  correctionId: number;
  appliedLive: boolean;
  rulesCreated: number;
  cautionApplied: boolean;
}

// ===== 첨삭 분석·확정 API =====

/** LLM 없이 즉시 PENDING 저장 (applyLive=true 시 라이브 교체 포함) */
export async function saveCorrection(req: SaveCorrectionRequest): Promise<SaveCorrectionResponse> {
  const res = await api.post<SaveCorrectionResponse>(
    '/api/admin/content/corrections/save',
    req
  );
  return res.data;
}

/** PENDING 첨삭 전체를 백그라운드에서 일괄 LLM 분석 요청 */
export async function analyzeBatchCorrections(): Promise<BatchAnalyzeResponse> {
  const res = await api.post<BatchAnalyzeResponse>('/api/admin/ai-rules/history/analyze-batch');
  return res.data;
}

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

// ===== 첨삭 이력 API =====

export async function listCorrectionHistory(params: {
  page?: number;
  size?: number;
  status?: 'PENDING' | 'PROCESSED' | 'SKIPPED' | 'ALL';
}): Promise<PageResponse<AiCorrectionHistory>> {
  const queryParams: Record<string, any> = {
    page: params.page ?? 0,
    size: params.size ?? 20,
  };
  if (params.status && params.status !== 'ALL') queryParams.status = params.status;
  const res = await api.get<PageResponse<AiCorrectionHistory>>('/api/admin/ai-rules/history', {
    params: queryParams,
  });
  return res.data;
}

/** PENDING 첨삭을 Sonnet으로 분석 — DB 미변경 */
export async function analyzeCorrectionHistory(corrId: number): Promise<AnalyzeResponse> {
  const res = await api.post<AnalyzeResponse>(`/api/admin/ai-rules/history/${corrId}/analyze`);
  return res.data;
}

/** 분석 결과를 페르소나 / 전체 규칙으로 적용 */
export async function applyCorrectionHistory(
  corrId: number,
  req: ApplyHistoryRequest
): Promise<ApplyHistoryResponse> {
  const res = await api.post<ApplyHistoryResponse>(
    `/api/admin/ai-rules/history/${corrId}/apply`,
    req
  );
  return res.data;
}

/** PENDING 첨삭을 SKIPPED로 표시 */
export async function skipCorrectionHistory(corrId: number): Promise<void> {
  await api.patch(`/api/admin/ai-rules/history/${corrId}/skip`);
}

// ===== 일괄 분석 (map-reduce) Types =====

export interface GlobalRuleProposal {
  ruleText: string;
  scope: 'ALL' | 'POST' | 'COMMENT';
  sourceCorrIds: number[];
  rationale: string;
}

export interface PersonaCautionProposal {
  personaId: string;
  cautionText: string;
  sourceCorrIds: number[];
  rationale: string;
}

export interface BatchPlan {
  globalRules: GlobalRuleProposal[];
  personaCautions: PersonaCautionProposal[];
  allSourceCorrIds: number[];
}

export type BatchJobStatus = 'RUNNING' | 'READY' | 'FAILED';

export interface BatchJobSnapshot {
  jobId: string;
  status: BatchJobStatus;
  pendingCount: number;
  chunksDone: number;
  chunksTotal: number;
  plan: BatchPlan | null;
  error: string | null;
}

export interface ApprovedGlobalRule {
  ruleText: string;
  scope: 'ALL' | 'POST' | 'COMMENT';
  sourceCorrIds: number[];
}

export interface ApprovedPersonaCaution {
  personaId: string;
  cautionText: string;
  sourceCorrIds: number[];
}

export interface ApplyBatchRequest {
  globalRules: ApprovedGlobalRule[];
  personaCautions: ApprovedPersonaCaution[];
  pushToBank: boolean;
}

export interface ConsolidatedApplyResult {
  rulesCreated: number;
  cautionsApplied: number;
  corrProcessed: number;
}

// ===== 일괄 분석 API =====

/** PENDING 첨삭 일괄 분석 시작 → jobId 반환 */
export async function startBatchAnalysis(): Promise<BatchAnalyzeResponse> {
  const res = await api.post<BatchAnalyzeResponse>('/api/admin/ai-rules/history/analyze-batch');
  return res.data;
}

/** 일괄 분석 job 상태 폴링 */
export async function getBatchAnalysisJob(jobId: string): Promise<BatchJobSnapshot> {
  const res = await api.get<BatchJobSnapshot>(`/api/admin/ai-rules/history/analyze-batch/${jobId}`);
  return res.data;
}

/** 관리자 승인된 플랜 적용 (LLM 없음) */
export async function applyBatchPlan(req: ApplyBatchRequest): Promise<ConsolidatedApplyResult> {
  const res = await api.post<ConsolidatedApplyResult>('/api/admin/ai-rules/history/apply-batch-plan', req);
  return res.data;
}

// ===== 기본 프롬프트 템플릿 API =====

export interface AiPromptTemplate {
  key: string;
  description: string | null;
  content: string;
  updatedAt: string | null;
  updatedBy: string | null;
}

export async function listPromptTemplates(): Promise<AiPromptTemplate[]> {
  const res = await api.get<AiPromptTemplate[]>('/api/admin/ai-rules/prompts');
  return res.data;
}

export async function getPromptTemplate(key: string): Promise<AiPromptTemplate> {
  const res = await api.get<AiPromptTemplate>(`/api/admin/ai-rules/prompts/${key}`);
  return res.data;
}

export async function updatePromptTemplate(key: string, content: string): Promise<AiPromptTemplate> {
  const res = await api.put<AiPromptTemplate>(`/api/admin/ai-rules/prompts/${key}`, { content });
  return res.data;
}
