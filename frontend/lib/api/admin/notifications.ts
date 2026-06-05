import { api } from '@/lib/api/client';

export interface BroadcastNotificationRequest {
  title: string;
  subtitle: string;
  target: 'ALL' | 'MEMBERS' | 'CUSTOM';
  userIds?: string[];
  message?: string;
}

/**
 * 알림 브로드캐스트
 */
export async function broadcastNotification(
  request: BroadcastNotificationRequest
): Promise<void> {
  await api.post('/api/admin/notifications/broadcast', request);
}
