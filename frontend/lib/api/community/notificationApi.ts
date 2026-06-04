import { api } from '../client';

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  subtitle?: string;
  refPostId?: string;
  refCommentId?: number;
  isRead: boolean;
  createdAt: string;
}

export const notificationApi = {
  list: () =>
    api.get<NotificationItem[]>('/api/notifications').then(r => r.data),

  readAll: () =>
    api.post('/api/notifications/read-all'),
};
