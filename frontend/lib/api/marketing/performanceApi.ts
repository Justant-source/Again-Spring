import { api } from '../client';
import type { ContentResponse } from './contentApi';

export interface PerformanceData {
  impressions?: number;
  likes?: number;
  comments?: number;
  shares?: number;
  clicks?: number;
  saves?: number;
  note?: string;
}

export async function updatePerformance(id: number, data: PerformanceData): Promise<ContentResponse> {
  const res = await api.put<ContentResponse>(`/api/admin/marketing/contents/${id}/performance`, data);
  return res.data;
}

export async function scheduleContent(id: number, scheduledAt: string): Promise<ContentResponse> {
  const res = await api.put<ContentResponse>(`/api/admin/marketing/contents/${id}/schedule`, { scheduledAt });
  return res.data;
}

export async function publishContent(id: number, publishedUrl?: string): Promise<ContentResponse> {
  const res = await api.put<ContentResponse>(`/api/admin/marketing/contents/${id}/publish`, { publishedUrl });
  return res.data;
}
