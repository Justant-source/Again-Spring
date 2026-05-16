import { api } from './client';

export async function getAdminSummary() {
  const res = await api.get('/api/admin/dashboard/summary');
  return res.data as Record<string, number>;
}

export async function getAdminDailyStats() {
  const res = await api.get('/api/admin/dashboard/daily-stats');
  return res.data as Array<Record<string, unknown>>;
}

export async function getAdminRetention() {
  const res = await api.get('/api/admin/dashboard/retention');
  return res.data as Array<Record<string, unknown>>;
}

export async function getAdminFeedbacks(params?: { category?: string; status?: string; page?: number }) {
  const res = await api.get('/api/admin/feedbacks', { params });
  return res.data;
}

export async function updateFeedbackStatus(id: number, status: string, adminNote?: string) {
  const res = await api.patch(`/api/admin/feedbacks/${id}`, { status, adminNote });
  return res.data;
}

export async function searchUsers(q: string) {
  const res = await api.get('/api/admin/users/search', { params: { q } });
  return res.data;
}

export interface AdminUserListItem {
  id: string;
  email?: string;
  nickname: string;
  isGuest: boolean;
  provider?: string;
  roles?: string[];
  createdAt?: string;
  mbtiType?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // current page (0-based)
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
}

export async function listUsers(params: { page?: number; size?: number; includeGuest?: boolean } = {}) {
  const res = await api.get<PageResponse<AdminUserListItem>>('/api/admin/users', { params });
  return res.data;
}

export async function deleteUserData(id: string) {
  const res = await api.delete(`/api/admin/users/${id}/data`);
  return res.data;
}

export async function updateUserRoles(id: string, roles: string[]): Promise<{ userId: string; roles: string[] }> {
  const res = await api.patch(`/api/admin/users/${id}/roles`, { roles });
  return res.data;
}

export interface AdminUserDetail {
  id: string;
  email?: string;
  nickname: string;
  isGuest: boolean;
  mbtiType?: string;
  communicationStyle?: string;
  provider?: string;
  roles?: string[];
  createdAt?: string;
  deletedAt?: string;
  onboardingCompletedAt?: string;
  termsAgreedAt?: string;
  privacyAgreedAt?: string;
  disclaimerAgreedAt?: string;
  marketingAgreedAt?: string;
  totalSessions: number;
  completedSessions: number;
  feedbackCount: number;
  lastSessionAt?: string;
}

export async function getAdminUserDetail(id: string): Promise<AdminUserDetail> {
  const res = await api.get<AdminUserDetail>(`/api/admin/users/${id}`);
  return res.data;
}

export interface CrisisMessage {
  messageId: number;
  sessionId: string;
  sender: 'USER_A' | 'USER_B' | 'MEDIATOR_TO_A' | 'MEDIATOR_TO_B';
  crisisLevel: number;
  charCount: number;
  createdAt: string;
}

export async function getCrisisRecent(limit = 20): Promise<CrisisMessage[]> {
  const res = await api.get<CrisisMessage[]>('/api/admin/dashboard/crisis-recent', {
    params: { limit },
  });
  return res.data;
}

// V11: 시스템 헬스
export type HealthStatus = 'OK' | 'WARN' | 'ERROR';

export interface ComponentHealth {
  status: HealthStatus;
  message?: string;
  details?: Record<string, unknown>;
}

export interface SystemHealth {
  checkedAt: string;
  components: {
    backend: ComponentHealth;
    database: ComponentHealth;
    smtp: ComponentHealth;
    anthropic: ComponentHealth;
  };
}

export async function getSystemHealth(): Promise<SystemHealth> {
  const res = await api.get<SystemHealth>('/api/admin/health/system');
  return res.data;
}

// V11: LLM 실패율
export interface LlmFailureRateRow {
  date: string;
  haikuTotal: number;
  haikuFallback: number;
  sonnetTotal: number;
  sonnetFallback: number;
}

export async function getLlmFailureRate(days = 7): Promise<LlmFailureRateRow[]> {
  const res = await api.get<LlmFailureRateRow[]>('/api/admin/dashboard/llm-failure-rate', {
    params: { days },
  });
  return res.data;
}
