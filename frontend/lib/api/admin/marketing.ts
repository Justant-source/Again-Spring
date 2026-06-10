import { api } from '@/lib/api/client';

// ===== Types =====

export interface MarketingJob {
  id: number;
  remoteJobId: string | null;
  postId: string;
  status: string; // REQUESTED|QUEUED|RUNNING|READY|PUBLISHING|PUBLISHED|FAILED|STALE
  phase: string | null;
  progress: number;
  targets: string[];
  autoPublish: boolean;
  artifacts: Record<string, string> | null; // { video_mp4, thumbnail, blog_md, ... }
  publications: Array<{ platform: string; state: string; url: string }> | null;
  errorMessage: string | null;
  pollFailCount: number;
  lastPolledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateMarketingJobRequest {
  postId: string;
  targets: string[];
  autoPublish: boolean;
}

// ===== API Functions =====

export async function createMarketingJob(
  postId: string,
  targets: string[],
  autoPublish: boolean
): Promise<MarketingJob> {
  const res = await api.post<MarketingJob>('/api/admin/marketing/jobs', {
    postId,
    targets,
    autoPublish,
  });
  return res.data;
}

export async function listMarketingJobs(): Promise<MarketingJob[]> {
  const res = await api.get<MarketingJob[]>('/api/admin/marketing/jobs');
  return res.data;
}

export async function getMarketingJob(id: number): Promise<MarketingJob> {
  const res = await api.get<MarketingJob>(`/api/admin/marketing/jobs/${id}`);
  return res.data;
}

export async function publishMarketingJob(id: number): Promise<MarketingJob> {
  const res = await api.post<MarketingJob>(`/api/admin/marketing/jobs/${id}/publish`);
  return res.data;
}

export async function republishMarketingJob(id: number): Promise<MarketingJob> {
  const res = await api.post<MarketingJob>(`/api/admin/marketing/jobs/${id}/republish`);
  return res.data;
}

// ===== Platform credentials =====
// NOTE: this payload is proxied verbatim from ASM (FastAPI) → snake_case keys,
// unlike the camelCase MarketingJob above. ASM is the single source of truth for
// the per-platform `fields` schema; secrets are never returned (only secret_set).

export interface CredentialFieldSpec {
  key: string;
  secret: boolean;
  required: boolean;
}

export interface PlatformCredentialStatus {
  platform: string;
  fields: CredentialFieldSpec[];
  configured: boolean;
  values: Record<string, string>; // public (non-secret) values only
  secret_set: Record<string, boolean>; // secret key -> whether a value is stored
  updated_at: string | null;
}

export async function listPlatformCredentials(): Promise<PlatformCredentialStatus[]> {
  const res = await api.get<PlatformCredentialStatus[]>('/api/admin/marketing/credentials');
  return res.data;
}

export async function upsertPlatformCredential(
  platform: string,
  values: Record<string, string>
): Promise<PlatformCredentialStatus> {
  const res = await api.put<PlatformCredentialStatus>(
    `/api/admin/marketing/credentials/${platform}`,
    { values }
  );
  return res.data;
}

export async function deletePlatformCredential(platform: string): Promise<void> {
  await api.delete(`/api/admin/marketing/credentials/${platform}`);
}

// ===== YouTube Shorts OAuth 2.0 =====

/**
 * OAuth start — Google 인증 URL 생성.
 * redirectUri: 팝업이 리다이렉트될 콜백 URL (등록된 허용 호스트여야 함).
 */
export async function startYoutubeOauth(redirectUri: string): Promise<{ auth_url: string }> {
  const res = await api.post<{ auth_url: string }>(
    '/api/admin/marketing/credentials/youtube_shorts/oauth/start',
    { redirect_uri: redirectUri }
  );
  return res.data;
}

/**
 * OAuth exchange — authorization code → refresh_token 저장.
 * code, state: 콜백 URL 쿼리파라미터에서 추출.
 */
export async function exchangeYoutubeOauth(
  code: string,
  state: string
): Promise<PlatformCredentialStatus> {
  const res = await api.post<PlatformCredentialStatus>(
    '/api/admin/marketing/credentials/youtube_shorts/oauth/exchange',
    { code, state }
  );
  return res.data;
}

// ===== Marketing Analytics =====

export interface PlatformStatsDto {
  platform: string;
  attempted: number;
  published: number;
  failed: number;
  successRate: number;
  lastPublishedUrl: string | null;
  lastPublishedAt: string | null;
}

export async function getMarketingPerformance(days?: number): Promise<PlatformStatsDto[]> {
  const params = new URLSearchParams();
  if (days !== undefined) params.append('days', String(days));
  const res = await api.get<PlatformStatsDto[]>(
    `/api/admin/marketing/performance${params.size > 0 ? '?' + params.toString() : ''}`
  );
  return res.data;
}

// ===== Publication Timeline =====

export interface TimelineEventDto {
  jobId: number;
  postId: string;
  platform: string;
  url: string | null;
  state: string;
  publishedAt: string | null;
}

export async function getPublicationTimeline(limit?: number): Promise<TimelineEventDto[]> {
  const params = new URLSearchParams();
  if (limit !== undefined) params.append('limit', String(limit));
  const res = await api.get<TimelineEventDto[]>(
    `/api/admin/marketing/timeline${params.size > 0 ? '?' + params.toString() : ''}`
  );
  return res.data;
}

// ===== Job Traffic =====

export interface JobTrafficDto {
  jobId: number;
  visits: number;
  uniqueSessions: number;
  bySources: Array<{ source: string; visits: number }>;
}

export async function getJobTraffic(id: number): Promise<JobTrafficDto> {
  const res = await api.get<JobTrafficDto>(`/api/admin/marketing/jobs/${id}/traffic`);
  return res.data;
}

// ===== Admin Posts for Picker (별도 타입 — content.ts의 AdminPost와 구분) =====

export interface PickerPost {
  id: string;
  title: string;
  authorNickname: string | null;
  voteCount: number;
  commentCount: number;
  createdAt: string;
}

export async function listAdminPostsForPicker(page?: number): Promise<PickerPost[]> {
  const params = new URLSearchParams();
  if (page !== undefined) params.append('page', String(page));
  params.append('size', '20');
  const res = await api.get<PickerPost[]>(
    `/api/admin/content/posts${params.size > 0 ? '?' + params.toString() : ''}`
  );
  return res.data;
}
