import { api } from '../client';

export interface StoryRequest {
  title: string;
  sourcePlatform: string;
  sourceUrl?: string;
  rawText: string;
  relationType: string;
}

export interface StoryResponse {
  id: number;
  title?: string;
  sourcePlatform: string;
  sourceUrl?: string;
  rawText: string;
  category?: string;
  relationType: string;
  status: string;
  blockedReason?: string;
  createdBy: string;
  createdAt: string;
}

export interface StorySummaryResponse {
  id: number;
  title?: string;
  sourcePlatform: string;
  relationType: string;
  status: string;
  createdAt: string;
}

export async function createStory(req: StoryRequest): Promise<StoryResponse> {
  const res = await api.post<StoryResponse>('/api/admin/marketing/stories', req);
  return res.data;
}

export async function getStories(status?: string): Promise<StorySummaryResponse[]> {
  const res = await api.get<StorySummaryResponse[]>('/api/admin/marketing/stories', {
    params: status ? { status } : undefined,
  });
  return res.data;
}

export async function getStory(id: number): Promise<StoryResponse> {
  const res = await api.get<StoryResponse>(`/api/admin/marketing/stories/${id}`);
  return res.data;
}

export async function approveStory(id: number): Promise<StoryResponse> {
  const res = await api.post<StoryResponse>(`/api/admin/marketing/stories/${id}/approve`);
  return res.data;
}

export async function rejectStory(id: number, reason: string): Promise<StoryResponse> {
  const res = await api.post<StoryResponse>(`/api/admin/marketing/stories/${id}/reject`, { reason });
  return res.data;
}

export async function deleteStory(id: number): Promise<void> {
  await api.delete(`/api/admin/marketing/stories/${id}`);
}
