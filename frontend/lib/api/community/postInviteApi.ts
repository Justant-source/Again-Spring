import { api } from '../client';

export interface InviteResponse {
  inviteToken: string;
  inviteUrl: string;
}

export interface InvitePreview {
  postId: string;
  userTitle: string;
  authorBodyPublished: string;
  category: string;
}

export interface SubmitAnswerRequest {
  userTitle?: string;
  bodyRaw: string;
}

export interface PublishModeRequest {
  mode: string;
  voteDurationHours: number;
}

export const postInviteApi = {
  createInvite: (postId: string) =>
    api.post<InviteResponse>(`/api/community/posts/${postId}/invite`).then(r => r.data),

  getByToken: (token: string) =>
    api.get<InvitePreview>(`/api/s/${token}`).then(r => r.data),

  submitAnswer: (token: string, req: SubmitAnswerRequest) =>
    api.post(`/api/s/${token}/answer`, req),

  setPublishMode: (postId: string, mode: string, voteDurationHours: number) =>
    api.patch(`/api/community/posts/${postId}/publish-mode`, { mode, voteDurationHours }),

  publishNow: (postId: string) =>
    api.post(`/api/community/posts/${postId}/publish-now`),
};
