import { api } from '../client';

export interface ContentResponse {
  id: number;
  simulationId: number;
  platform: 'x' | 'instagram' | 'naver_blog' | 'threads' | 'facebook';
  title?: string;
  bodyText: string;
  hashtags?: string[];
  imagePaths?: string;
  status: 'GENERATING' | 'DRAFT' | 'REVIEW' | 'APPROVED' | 'EXPORTED' | 'REJECTED';
  safetyCheckJson?: string;
  createdAt: string;
}

export interface ContentSummaryResponse {
  id: number;
  simulationId: number;
  platform: string;
  status: string;
  createdAt: string;
  imagePaths?: string;
}

export async function generateContent(
  simulationId: number,
  platform: 'x' | 'instagram' | 'naver_blog'
): Promise<ContentResponse> {
  const res = await api.post<ContentResponse>(
    `/api/admin/marketing/contents/generate?simulationId=${simulationId}&platform=${platform}`
  );
  return res.data;
}

export async function getContents(): Promise<ContentSummaryResponse[]> {
  const res = await api.get<ContentSummaryResponse[]>('/api/admin/marketing/contents');
  return res.data;
}

export async function getContent(id: number): Promise<ContentResponse> {
  const res = await api.get<ContentResponse>(`/api/admin/marketing/contents/${id}`);
  return res.data;
}

export async function updateContent(id: number, bodyText: string): Promise<ContentResponse> {
  const res = await api.put<ContentResponse>(`/api/admin/marketing/contents/${id}`, { bodyText });
  return res.data;
}

export async function approveContent(id: number): Promise<ContentResponse> {
  const res = await api.post<ContentResponse>(`/api/admin/marketing/contents/${id}/approve`);
  return res.data;
}

export async function rejectContent(id: number, reason: string): Promise<ContentResponse> {
  const res = await api.post<ContentResponse>(
    `/api/admin/marketing/contents/${id}/reject?reason=${encodeURIComponent(reason)}`
  );
  return res.data;
}

export async function deleteContent(id: number): Promise<void> {
  await api.delete(`/api/admin/marketing/contents/${id}`);
}
