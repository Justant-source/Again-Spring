import { api as client } from '@/lib/api/client';

/**
 * 마케팅 후보 사연 — 커뮤니티 게시글 picker API
 */

export interface CandidatePostResponse {
  id: string;
  title: string;
  snippet: string;
  category: string;
  categoryDisplayName: string;
  authorPct: number;
  partnerPct: number;
  voteCount: number;
  commentCount: number;
  viewCount: number;
  createdAt: string;
  synthetic: boolean;
}

export interface CandidatePostParams {
  sortBy?: 'recommended' | 'latest';
  category?: string;
  q?: string;
  page?: number;
  size?: number;
}

export async function getCandidatePosts(
  params: CandidatePostParams = {}
): Promise<CandidatePostResponse[]> {
  const { data } = await client.get('/api/admin/marketing/candidate-posts', {
    params: {
      sortBy: params.sortBy ?? 'recommended',
      category: params.category,
      q: params.q,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return data;
}
