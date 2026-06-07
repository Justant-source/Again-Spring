import { api } from '../client';

export interface ContentResponse {
  id: number;
  sourcePostId: string | null;
  platform: 'x' | 'instagram' | 'naver_blog' | 'threads' | 'facebook';
  title?: string;
  bodyText: string;
  hashtags?: string[];
  imagePaths?: string;
  status: 'GENERATING' | 'DRAFT' | 'REVIEW' | 'APPROVED' | 'EXPORTED' | 'REJECTED' | 'PUBLISHING' | 'PARTIAL' | 'PUBLISHED' | 'FAILED';
  safetyCheckJson?: string;
  createdAt: string;
  scheduledAt?: string;
  publishedAt?: string;
  publishedUrl?: string;
  performanceJson?: string;
  updatedAt?: string;
}

export interface ContentSummaryResponse {
  id: number;
  sourcePostId: string | null;
  platform: string;
  status: string;
  createdAt: string;
  imagePaths?: string;
}

/**
 * 커뮤니티 게시글로부터 마케팅 콘텐츠를 생성한다.
 * platforms 미지정 시 서버에서 x, instagram, naver_blog 3종 동시 생성.
 */
export async function generateFromPost(
  postId: string,
  platforms?: ('x' | 'instagram' | 'naver_blog' | 'threads' | 'facebook')[]
): Promise<ContentResponse[]> {
  const params = new URLSearchParams();
  params.set('postId', postId);
  if (platforms && platforms.length > 0) {
    platforms.forEach((p) => params.append('platforms', p));
  }
  const res = await api.post<ContentResponse[]>(
    `/api/admin/marketing/contents/generate-from-post?${params.toString()}`
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
