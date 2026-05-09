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

export async function deleteUserData(id: string) {
  const res = await api.delete(`/api/admin/users/${id}/data`);
  return res.data;
}
