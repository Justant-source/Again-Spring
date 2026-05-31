import { api } from '../client';

export type SocialPlatform = 'X' | 'INSTAGRAM';
export type SocialState = 'PENDING' | 'SUCCEEDED' | 'FAILED';
export type SessionStatus = 'SEEDED' | 'EXPIRED' | 'NOT_SEEDED';

export interface SocialPublishResult {
  platform: SocialPlatform;
  state: SocialState;
  publishedUrl?: string;
  errorReason?: string;
  attemptedAt?: string;
}

export interface SocialPublishStatusResponse {
  contentId: number;
  contentStatus: string;
  results: SocialPublishResult[];
}

export interface CredentialStatus {
  platform: SocialPlatform;
  configured: boolean;
}

export interface SessionStatusInfo {
  platform: SocialPlatform;
  status: SessionStatus;
  lastUsedAt?: string;
}

export async function publishSocial(
  contentId: number,
  targets: SocialPlatform[],
  linkMode: 'last_tweet' | 'first_reply'
): Promise<{ contentId: number; results: SocialPublishResult[] }> {
  const res = await api.post(`/api/admin/marketing/social/publish/${contentId}`, { targets, linkMode });
  return res.data;
}

export async function getPublishStatus(contentId: number): Promise<SocialPublishStatusResponse> {
  const res = await api.get<SocialPublishStatusResponse>(`/api/admin/marketing/social/publish/${contentId}/status`);
  return res.data;
}

export async function saveCredentials(
  platform: SocialPlatform,
  email: string,
  password: string
): Promise<void> {
  await api.post('/api/admin/marketing/social/credentials', { platform, email, password });
}

export async function testLogin(platform: SocialPlatform): Promise<{ ok: boolean; error: string | null }> {
  const res = await api.post<{ ok: boolean; error: string | null }>(`/api/admin/marketing/social/test-login/${platform}`);
  return res.data;
}

export async function getCredentialStatus(): Promise<CredentialStatus[]> {
  const res = await api.get<CredentialStatus[]>('/api/admin/marketing/social/credentials/status');
  return res.data;
}

export async function seedSession(platform: SocialPlatform, storageState: string): Promise<void> {
  await api.post('/api/admin/marketing/social/sessions', { platform, storageState });
}

export async function getSessionStatus(): Promise<SessionStatusInfo[]> {
  const res = await api.get<SessionStatusInfo[]>('/api/admin/marketing/social/sessions/status');
  return res.data;
}
