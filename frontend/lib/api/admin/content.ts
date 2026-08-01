import { api } from '@/lib/api/client';
import type { PageResponse } from '../admin';
export type { PageResponse };

// ===== Types =====

export interface AdminPost {
  id: string;
  authorId: string;
  title: string;
  category: string;
  status: string;
  viewCount: number;
  likeCount?: number;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  deletedByAdminId: string | null;
  jurorCount?: number;
  userTitle?: string;
  bodyRaw?: string;
  bodyPublished?: string;
  partnerBodyRaw?: string;
  partnerBodyPublished?: string;
  voteCloseAt?: string;
  visibility?: string;
  publishMode?: string;
  neutralizationPassed?: boolean;
  /** AI 봇 작성 여부. ADMIN 전용 — 공개 API 미노출. */
  synthetic?: boolean;
  /** 관리자가 수동 생성한 항목 여부. */
  createdByAdmin?: boolean;
  /** 미삭제 댓글·대댓글 수 (목록용). */
  commentCount?: number;
  /** 원본 비교 기능: 재구성 모드로 생성된 글의 크롤 원본 example_bank ID. null이면 원본 없음. */
  sourceExampleId?: number | null;
  sourceCommunity?: string | null;
  sourceUrl?: string | null;
  sourceOriginalTitle?: string | null;
}

export interface AdminComment {
  id: number;
  postId: string;
  parentCommentId: number | null;
  authorId: string;
  body: string;
  status: string;
  likeCount: number;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  deletedByAdminId: string | null;
  /** AI 봇 작성 여부. ADMIN 전용 — 공개 API 미노출. */
  synthetic?: boolean;
  /** 관리자가 수동 생성한 항목 여부. */
  createdByAdmin?: boolean;
}

// ===== Posts API =====

export async function listAdminPosts(params: {
  page?: number;
  size?: number;
  synthetic?: boolean;
  category?: string;
  search?: string;
}): Promise<PageResponse<AdminPost>> {
  const queryParams: Record<string, any> = {
    page: params.page ?? 0,
    size: params.size ?? 20,
  };

  if (params.synthetic !== undefined) {
    queryParams.synthetic = params.synthetic;
  }
  if (params.category && params.category !== 'ALL') {
    queryParams.category = params.category;
  }
  if (params.search) {
    queryParams.search = params.search;
  }

  const res = await api.get<PageResponse<AdminPost>>('/api/admin/content/posts', {
    params: queryParams,
  });
  return res.data;
}

export async function getAdminPost(postId: string): Promise<AdminPost> {
  const res = await api.get<AdminPost>(`/api/admin/content/posts/${postId}`);
  return res.data;
}

export async function updatePost(
  postId: string,
  data: Partial<{ title: string; bodyRaw: string; partnerBodyRaw: string; status: string; category: string; viewCount: number }>
): Promise<AdminPost> {
  const res = await api.patch<AdminPost>(`/api/admin/content/posts/${postId}`, data);
  return res.data;
}

// ===== 원본 비교 API =====

export interface SourceData {
  community: string | null;
  url: string | null;
  title: string | null;
  body: string | null;
}

export interface GeneratedData {
  title: string | null;
  body: string | null;
}

export interface SourceComparisonResponse {
  /** 이 글이 AI 봇 생성 글인지 */
  synthetic: boolean;
  /** 크롤 원본 1:1 링크가 있는지 */
  hasSource: boolean;
  source: SourceData | null;
  generated: GeneratedData | null;
}

/** 원본 비교 화면용 데이터: 크롤 원본(왼쪽) + AI 생성본(오른쪽) */
export async function getSourceComparison(postId: string): Promise<SourceComparisonResponse> {
  const res = await api.get<SourceComparisonResponse>(
    `/api/admin/content/posts/${postId}/source-comparison`
  );
  return res.data;
}

export async function deletePost(postId: string): Promise<void> {
  await api.delete(`/api/admin/content/posts/${postId}`);
}

export async function blockPost(postId: string): Promise<void> {
  await api.post(`/api/admin/content/posts/${postId}/block`);
}

export async function unblockPost(postId: string): Promise<void> {
  await api.post(`/api/admin/content/posts/${postId}/unblock`);
}

export async function adjustPostLikes(
  postId: string,
  delta: 1 | -1
): Promise<{ likeCount: number }> {
  const res = await api.post<{ likeCount: number }>(
    `/api/admin/content/posts/${postId}/likes/adjust`,
    { delta }
  );
  return res.data;
}

export async function createPost(data: {
  title: string;
  bodyRaw: string;
  category: string;
  authorId: string;
}): Promise<AdminPost> {
  const res = await api.post<AdminPost>('/api/admin/content/posts', data);
  return res.data;
}

// ===== Comments API =====

export async function listAdminComments(params: {
  page?: number;
  size?: number;
  status?: string;
  search?: string;
}): Promise<PageResponse<AdminComment>> {
  const queryParams: Record<string, any> = {
    page: params.page ?? 0,
    size: params.size ?? 20,
  };

  if (params.status && params.status !== 'ALL') {
    queryParams.status = params.status;
  }
  if (params.search) {
    queryParams.search = params.search;
  }

  const res = await api.get<PageResponse<AdminComment>>('/api/admin/content/comments', {
    params: queryParams,
  });
  return res.data;
}

export async function getAdminComment(commentId: number): Promise<AdminComment> {
  const res = await api.get<AdminComment>(`/api/admin/content/comments/${commentId}`);
  return res.data;
}

export async function updateComment(
  commentId: number,
  data: { body?: string }
): Promise<AdminComment> {
  const res = await api.patch<AdminComment>(`/api/admin/content/comments/${commentId}`, data);
  return res.data;
}

export async function deleteComment(commentId: number): Promise<void> {
  await api.delete(`/api/admin/content/comments/${commentId}`);
}

export async function blockComment(commentId: number): Promise<void> {
  await api.post(`/api/admin/content/comments/${commentId}/block`);
}

export async function unblockComment(commentId: number): Promise<void> {
  await api.post(`/api/admin/content/comments/${commentId}/unblock`);
}

export async function adjustCommentLikes(
  commentId: number,
  delta: 1 | -1
): Promise<{ likeCount: number }> {
  const res = await api.post<{ likeCount: number }>(
    `/api/admin/content/comments/${commentId}/likes/adjust`,
    { delta }
  );
  return res.data;
}

export async function createComment(data: {
  postId: string;
  parentCommentId?: number | null;
  body: string;
  authorId: string;
}): Promise<AdminComment> {
  const res = await api.post<AdminComment>('/api/admin/content/comments', data);
  return res.data;
}

// ===== Scheduled holdings (ai_scheduled_posts via orchestrator proxy) =====

export interface ScheduledHoldingSummary {
  id: string;
  personaId: string;
  title: string;
  category: string | null;
  status: string;
  scheduledPublishAt: string | null;
  itemCount: number;
  origin?: string;
  createdAt?: string | null;
  failureCode?: string | null;
}

export interface ScheduledHoldingItem {
  ref: string;
  parentRef?: string | null;
  personaId: string;
  body: string;
  type: 'COMMENT' | 'REPLY';
  scheduledAt: string | null;
  stance?: string;
  priority?: number;
}

export interface ScheduledHoldingDetail extends ScheduledHoldingSummary {
  body: string;
  provider?: string | null;
  model?: string | null;
  publishedPostId?: string | null;
  items: ScheduledHoldingItem[];
}

export async function listScheduledHoldings(status?: string): Promise<ScheduledHoldingSummary[]> {
  const res = await api.get<ScheduledHoldingSummary[]>('/api/admin/content/scheduled-posts', {
    params: status ? { status } : undefined,
  });
  return res.data ?? [];
}

export async function getScheduledHolding(id: string): Promise<ScheduledHoldingDetail> {
  const res = await api.get<ScheduledHoldingDetail>(`/api/admin/content/scheduled-posts/${id}`);
  return res.data;
}

export async function updateScheduledHolding(
  id: string,
  data: Partial<{
    title: string;
    body: string;
    category: string;
    scheduledPublishAt: string;
    items: Array<{
      ref: string;
      parentRef?: string | null;
      personaId: string;
      body: string;
      scheduledAt: string;
      stance?: string;
      priority?: number;
    }>;
  }>
): Promise<ScheduledHoldingDetail> {
  const res = await api.patch<ScheduledHoldingDetail>(
    `/api/admin/content/scheduled-posts/${id}`,
    data
  );
  return res.data;
}

export async function cancelScheduledHolding(id: string): Promise<ScheduledHoldingSummary> {
  const res = await api.delete<ScheduledHoldingSummary>(`/api/admin/content/scheduled-posts/${id}`);
  return res.data;
}

// ===== Published thread (same frame as scheduled holdings) =====

export interface PublishedThreadItem {
  id: number;
  parentCommentId?: number | null;
  authorId: string;
  body: string;
  type: 'COMMENT' | 'REPLY';
  createdAt: string | null;
  status?: string | null;
  synthetic?: boolean;
  likeCount?: number;
}

export interface PublishedThreadDetail {
  id: string;
  title: string;
  body: string;
  category: string | null;
  status: string | null;
  createdAt: string | null;
  viewCount?: number;
  authorId?: string;
  synthetic?: boolean;
  commentCount: number;
  items: PublishedThreadItem[];
}

export async function getPublishedThread(postId: string): Promise<PublishedThreadDetail> {
  const res = await api.get<PublishedThreadDetail>(`/api/admin/content/posts/${postId}/thread`);
  return res.data;
}

export async function updatePublishedThread(
  postId: string,
  data: Partial<{
    title: string;
    body: string;
    category: string;
    status: string;
    viewCount: number;
    createdAt: string;
    items: Array<{
      id: number;
      body?: string;
      authorId?: string;
      createdAt?: string;
    }>;
  }>
): Promise<PublishedThreadDetail> {
  const res = await api.patch<PublishedThreadDetail>(
    `/api/admin/content/posts/${postId}/thread`,
    data
  );
  return res.data;
}
