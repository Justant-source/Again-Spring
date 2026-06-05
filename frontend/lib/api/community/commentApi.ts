import { api } from '../client';

export interface Comment {
  id: number;
  authorId: string;
  authorNickname?: string;
  body: string;
  likeCount: number;
  isLiked: boolean;
  createdAt: string;
  replies?: Comment[];
  isAuthor?: boolean;
  isPartner?: boolean;
  /** 현재 사용자가 이 댓글의 작성자 — 수정·삭제 노출 판단 */
  isMine?: boolean;
}

export type CommentResponse = Comment;

export const commentApi = {
  list: (postId: string, page = 0, size = 10) =>
    api.get<Comment[]>(`/api/community/posts/${postId}/comments`, { params: { page, size } }).then(r => r.data),

  add: (postId: string, body: string, parentCommentId?: number) =>
    api.post<Comment>(`/api/community/posts/${postId}/comments`, { body, parentCommentId }).then(r => r.data),

  update: (postId: string, commentId: number, body: string) =>
    api.put<Comment>(`/api/community/posts/${postId}/comments/${commentId}`, { body }).then(r => r.data),

  remove: (postId: string, commentId: number) =>
    api.delete(`/api/community/posts/${postId}/comments/${commentId}`),

  toggleLike: (postId: string, commentId: number) =>
    api.post<{ liked: boolean; count: number }>(
      `/api/community/posts/${postId}/comments/${commentId}/like`).then(r => r.data),

  report: (postId: string, commentId: number, reason: string) =>
    api.post(`/api/community/posts/${postId}/comments/${commentId}/report`, { reason }),

  blockUser: (userId: string) =>
    api.post(`/api/community/users/${userId}/block`),
};
