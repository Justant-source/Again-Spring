import { api } from '../client';

export interface PostCreateRequest {
  bodyRaw: string;
  category: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  userTitle?: string;
  jurorCount?: number;
  sessionId?: string;
}

export interface VoteOption {
  id: number;
  label: string;
  orderIdx: number;
}

export interface PostDetail {
  id: string;
  title: string;
  bodyPublished: string;
  category: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  status: string;
  voteOptions: VoteOption[];
  createdAt: string;
  isVoted?: boolean;
  commentCount?: number;
  viewCount?: number;
  voteResult?: VoteResult;
  userTitle?: string;
  jurorCount?: number;
  authorPct?: number;
  partnerPct?: number;
  paired?: boolean;
  partnerAnsweredAt?: string;
  inviteToken?: string;
  partnerBodyPublished?: string;
  isAuthor?: boolean;
  hasVoted?: boolean;
  myVoteSide?: 'g' | 'r' | null;
}

export interface PostSummary {
  id: string;
  title: string;
  userTitle?: string;
  category: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  status: string;
  voteCount: number;
  commentCount?: number;
  viewCount?: number;
  createdAt: string;
  authorPct?: number;
  partnerPct?: number;
  paired?: boolean;
  bodyPublished?: string;
  authorNickname?: string;
}

export interface VoteResult {
  options: Array<{ id: number; label: string; count: number; percentage: number }>;
  totalVotes: number;
  myVotedOptionId?: number;
}

export interface JuryResult {
  jurors: Array<{
    ageGroup: string;
    gender: string;
    chosenOptionLabel: string;
    empathyComment: string;
  }>;
  distribution: Array<{ label: string; count: number; percentage: number }>;
  legalNotice: string;
  summaryLine?: string;
}

export const postApi = {
  create: (req: PostCreateRequest) =>
    api.post<PostDetail>('/api/community/posts', req).then(r => r.data),

  list: (params?: { category?: string; page?: number; size?: number; sort?: 'latest' | 'recommended' }) => {
    const { sort, ...rest } = params || {};
    const queryParams = { ...rest, ...(sort ? { sortBy: sort } : {}) };
    return api.get<{ content: PostSummary[]; totalElements: number; totalPages: number }>(
      '/api/community/posts', { params: queryParams }).then(r => r.data);
  },

  get: (id: string) =>
    api.get<PostDetail>(`/api/community/posts/${id}`).then(r => r.data),

  delete: (id: string) =>
    api.delete(`/api/community/posts/${id}`),

  vote: (postId: string, optionId: number) =>
    api.post<VoteResult>(`/api/community/posts/${postId}/vote`, { optionId }).then(r => r.data),

  getJury: (postId: string) =>
    api.get<JuryResult>(`/api/community/posts/${postId}/jury`).then(r => r.data),

  report: (postId: string, reason: string) =>
    api.post(`/api/community/posts/${postId}/report`, { reason }),

  toggleLike: (postId: string) =>
    api.post<{ liked: boolean; count: number }>(`/api/community/posts/${postId}/like`).then(r => r.data),

  recordView: (postId: string, deviceId: string) =>
    api.post<{ viewCount: number }>(`/api/community/posts/${postId}/view`, { deviceId }).then(r => r.data),
};
