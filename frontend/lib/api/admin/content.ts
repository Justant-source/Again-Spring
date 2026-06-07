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
  data: Partial<{ title: string; bodyRaw: string; partnerBodyRaw: string; status: string; category: string }>
): Promise<AdminPost> {
  const res = await api.patch<AdminPost>(`/api/admin/content/posts/${postId}`, data);
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
