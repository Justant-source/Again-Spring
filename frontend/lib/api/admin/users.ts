import { api } from '../client';
import type { AdminUserListItem, PageResponse } from '../admin';
export type { AdminUserListItem, PageResponse };

/**
 * 사용자 목록 조회 (페이지네이션)
 */
export async function listUsers(params: { page?: number; size?: number; includeGuest?: boolean } = {}) {
  const res = await api.get<PageResponse<AdminUserListItem>>('/api/admin/users', { params });
  return res.data;
}

/**
 * 사용자 정지
 */
export async function suspendUser(
  userId: string,
  data: { reason: string; suspendedUntil?: string | null }
): Promise<{ userId: string; status: string; suspendedUntil: string | null; suspendedReason: string }> {
  const res = await api.post(`/api/admin/users/manage/${userId}/suspend`, data);
  return res.data;
}

/**
 * 사용자 정지 해제
 */
export async function unsuspendUser(userId: string): Promise<{ userId: string; status: string }> {
  const res = await api.post(`/api/admin/users/manage/${userId}/unsuspend`);
  return res.data;
}

/**
 * 사용자 강제 로그아웃
 */
export async function forceLogoutUser(userId: string): Promise<{ userId: string; tokensInvalidatedAt: string }> {
  const res = await api.post(`/api/admin/users/manage/${userId}/force-logout`);
  return res.data;
}

/**
 * 사용자 익명화
 */
export async function anonymizeUser(userId: string): Promise<{ status: string; userId: string }> {
  const res = await api.delete(`/api/admin/users/${userId}/data`);
  return res.data;
}

/**
 * 사용자 CSV 내보내기 (다운로드 트리거)
 */
export async function exportUsersAsCSV(): Promise<void> {
  try {
    const response = await api.get('/api/admin/users/manage/export', {
      responseType: 'blob',
    });

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `users-${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    link.parentNode?.removeChild(link);
    window.URL.revokeObjectURL(url);
  } catch (error) {
    console.error('Failed to export users as CSV:', error);
    throw error;
  }
}

/**
 * 닉네임 강제 변경
 */
export async function changeNickname(userId: string, nickname: string): Promise<{ userId: string; oldNickname: string; newNickname: string }> {
  const res = await api.patch<{ userId: string; oldNickname: string; newNickname: string }>(
    `/api/admin/users/manage/${userId}/nickname`,
    { nickname }
  );
  return res.data;
}
