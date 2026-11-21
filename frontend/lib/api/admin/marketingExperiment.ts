import { api } from '@/lib/api/client';

// ===== Types — 계약: docs/_active/marketing-breakout-loop.md "어드민 API" =====

export interface ExperimentSettings {
  enabled: boolean;
  topicBiasEnabled: boolean;
  minSamples: number;
}

export interface ExperimentArm {
  key: string;
  renderProfile: string;
  scriptStyle: string;
  commentCount: number;
  outroMode: string;
  maxDurationSec: number | null;
  enabled: boolean;
  description: string;
  assigned30d: number;
  outcomes60d: number;
  meanReward: number | null;
  breakouts: number;
}

export interface ExperimentTopic {
  platform: string;
  category: string;
  hookEmotion: string;
  n: number;
  meanReward: number | null;
  multiplier: number;
}

export interface ExperimentAccountPoint {
  platform: string;
  collectedOn: string; // YYYY-MM-DD
  followers: number | null;
}

export interface ExperimentOverview {
  settings: ExperimentSettings;
  arms: ExperimentArm[];
  topics: ExperimentTopic[];
  accounts: ExperimentAccountPoint[];
}

const BASE = '/api/admin/marketing/experiment';

export async function getExperiment(): Promise<ExperimentOverview> {
  const res = await api.get<ExperimentOverview>(BASE);
  return res.data;
}

export async function updateExperimentSettings(body: {
  enabled?: boolean;
  topicBiasEnabled?: boolean;
}): Promise<void> {
  await api.put(`${BASE}/settings`, body);
}

export async function setExperimentArmEnabled(key: string, enabled: boolean): Promise<void> {
  await api.patch(`${BASE}/arms/${encodeURIComponent(key)}`, { enabled });
}

export async function runExperimentScoring(): Promise<{ created: number }> {
  const res = await api.post<{ created: number }>(`${BASE}/score/run`);
  return res.data;
}

export interface PreviewBatchStarted {
  postIds: string[];
  arms: string[];
  platform: string;
}

/** 최근 공개 사연 N개 x arm(기본: baseline 제외 전부)을 자동 게시 없이 순차 렌더한다. 진행 중이면 409. */
export async function startPreviewBatch(
  body: { postCount?: number; arms?: string[]; platform?: string } = {}
): Promise<PreviewBatchStarted> {
  const res = await api.post<PreviewBatchStarted>('/api/admin/marketing/experiment/preview-batch', body);
  return res.data;
}
