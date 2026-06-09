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
