import { api } from '@/lib/api/client';

export interface Announcement {
  id: string;
  title: string;
  body: string;
  level: 'INFO' | 'WARN';
  isActive: boolean;
  startsAt?: string;
  endsAt?: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAnnouncementRequest {
  title: string;
  body: string;
  level?: 'INFO' | 'WARN';
  startsAt?: string;
  endsAt?: string;
}

export interface AnnouncementsPage {
  content: Announcement[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * 공지사항 목록 조회
 */
export async function getAnnouncements(
  page = 0,
  size = 20,
  isActive?: boolean
): Promise<AnnouncementsPage> {
  const params: Record<string, any> = { page, size };
  if (isActive !== undefined) {
    params.isActive = isActive;
  }

  const res = await api.get<AnnouncementsPage>('/api/admin/announcements', {
    params,
  });
  return res.data;
}

/**
 * 공지사항 상세 조회
 */
export async function getAnnouncement(id: string): Promise<Announcement> {
  const res = await api.get<Announcement>(`/api/admin/announcements/${id}`);
  return res.data;
}

/**
 * 공지사항 작성
 */
export async function createAnnouncement(
  request: CreateAnnouncementRequest
): Promise<Announcement> {
  const res = await api.post<Announcement>(
    '/api/admin/announcements',
    request
  );
  return res.data;
}

/**
 * 공지사항 수정
 */
export async function updateAnnouncement(
  id: string,
  request: CreateAnnouncementRequest
): Promise<Announcement> {
  const res = await api.patch<Announcement>(
    `/api/admin/announcements/${id}`,
    request
  );
  return res.data;
}

/**
 * 공지사항 삭제
 */
export async function deleteAnnouncement(id: string): Promise<void> {
  await api.delete(`/api/admin/announcements/${id}`);
}

/**
 * 공지사항 활성화
 */
export async function activateAnnouncement(id: string): Promise<void> {
  await api.post(`/api/admin/announcements/${id}/activate`);
}

/**
 * 공지사항 비활성화
 */
export async function deactivateAnnouncement(id: string): Promise<void> {
  await api.post(`/api/admin/announcements/${id}/deactivate`);
}

/**
 * 공지사항 알림 발송
 */
export async function notifyAnnouncement(id: string): Promise<void> {
  await api.post(`/api/admin/announcements/${id}/notify`);
}
