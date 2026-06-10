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
