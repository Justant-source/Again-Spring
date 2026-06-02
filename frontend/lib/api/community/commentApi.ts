import { api } from '../client';

export interface Comment {
  id: number;
  authorId: string;
  body: string;
  likeCount: number;
  isLiked: boolean;
  createdAt: string;
  replies?: Comment[];
}

export interface CommentResponse extends Comment {
  isAuthor?: boolean;
  isPartner?: boolean;
}

export const commentApi = {
  list: (postId: string) =>
    api.get<Comment[]>(`/api/community/posts/${postId}/comments`).then(r => r.data),

  add: (postId: string, body: string, parentCommentId?: number) =>
    api.post<Comment>(`/api/community/posts/${postId}/comments`, { body, parentCommentId }).then(r => r.data),

  toggleLike: (postId: string, commentId: number) =>
    api.post<{ liked: boolean; count: number }>(
      `/api/community/posts/${postId}/comments/${commentId}/like`).then(r => r.data),

  report: (postId: string, commentId: number, reason: string) =>
    api.post(`/api/community/posts/${postId}/comments/${commentId}/report`, { reason }),

  blockUser: (userId: string) =>
    api.post(`/api/community/users/${userId}/block`),
};
