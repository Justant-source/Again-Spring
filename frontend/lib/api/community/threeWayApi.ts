import { api } from '../client';

export interface ThreeWaySession {
  id: string;
  status: string;
  inviteToken: string;
  partyAUserId: string;
  partyBUserId?: string;
  category?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ThreeWayMessage {
  id: number;
  twsId: string;
  authorRole: 'PARTY_A' | 'PARTY_B' | 'MEDIATOR';
  content: string;
  createdAt: string;
  llmModel?: string;
}

export interface CreateThreeWayRequest {
  category: string;
}

export interface SendThreeWayMessageRequest {
  content: string;
  authorRole: 'PARTY_A' | 'PARTY_B';
}

/**
 * 3-way mediation session API client
 */
export const threeWayApi = {
  /**
   * Create a new 3-way session (Party A initiates)
   */
  create: (category: string): Promise<ThreeWaySession> =>
    api.post<ThreeWaySession>('/api/three-way', { category }).then((r) => r.data),

  /**
   * Join an existing 3-way session (Party B joins)
   */
  join: (token: string): Promise<ThreeWaySession> =>
    api.post<ThreeWaySession>(`/api/three-way/join/${token}`).then((r) => r.data),

  /**
   * Get session details
   */
  getSession: (id: string): Promise<ThreeWaySession> =>
    api.get<ThreeWaySession>(`/api/three-way/${id}`).then((r) => r.data),

  /**
   * Get conversation history
   */
  getMessages: (id: string): Promise<ThreeWayMessage[]> =>
    api.get<ThreeWayMessage[]>(`/api/three-way/${id}/messages`).then((r) => r.data),

  /**
   * Send a message to the session
   */
  sendMessage: (id: string, content: string, authorRole: 'PARTY_A' | 'PARTY_B'): Promise<ThreeWayMessage> =>
    api
      .post<ThreeWayMessage>(`/api/three-way/${id}/messages`, { content, authorRole })
      .then((r) => r.data),

  /**
   * Get the invite URL for Party B
   */
  getInviteUrl: (id: string): Promise<{ url: string }> =>
    api.get<{ url: string }>(`/api/three-way/${id}/invite-url`).then((r) => r.data),
};
