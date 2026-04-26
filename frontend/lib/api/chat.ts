import { api } from './client';
import type { Message, MessageMetadata, PartnerStatus, Session } from '../types/session';

export const chatApi = {
  send: (sessionId: string, content: string) =>
    api.post(`/api/sessions/${sessionId}/messages`, { content }),

  getMessages: (sessionId: string, since?: number) =>
    api.get<Message[]>(`/api/sessions/${sessionId}/messages${since ? `?since=${since}` : ''}`),

  getPartnerMessages: (sessionId: string) =>
    api.get<MessageMetadata[]>(`/api/sessions/${sessionId}/partner-messages`),

  getPartnerStatus: (sessionId: string) =>
    api.get<PartnerStatus>(`/api/sessions/${sessionId}/partner-status`),

  invite: (sessionId: string) =>
    api.post(`/api/sessions/${sessionId}/invite`),

  finalize: (sessionId: string) =>
    api.post(`/api/sessions/${sessionId}/finalize`),

  agreeFinalize: (sessionId: string) =>
    api.post(`/api/sessions/${sessionId}/finalize/agree`),

  declineFinalize: (sessionId: string) =>
    api.post(`/api/sessions/${sessionId}/finalize/decline`),
};
