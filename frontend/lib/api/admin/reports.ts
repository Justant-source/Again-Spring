import { api } from '@/lib/api/client';
import { PageResponse } from './content';

export interface AdminReport {
  id: number;
  targetType: 'POST' | 'COMMENT';
  targetId: string;
  reporterUserId?: string;
  reason: string;
  status: 'PENDING' | 'RESOLVED';
  createdAt: string;
  resolvedAt?: string;
  resolvedAction?: 'BLOCK_POST' | 'BLOCK_COMMENT' | 'DISMISS';
}

export async function listReports(params: {
  status?: 'PENDING' | 'RESOLVED';
  page: number;
  size: number;
}): Promise<PageResponse<AdminReport>> {
  const queryParams = new URLSearchParams();
  if (params.status) queryParams.append('status', params.status);
  queryParams.append('page', params.page.toString());
  queryParams.append('size', params.size.toString());

  const res = await api.get<PageResponse<AdminReport>>(
    `/api/admin/reports?${queryParams.toString()}`
  );
  return res.data;
}

export async function getPendingCount(): Promise<{ count: number }> {
  const res = await api.get<{ count: number }>(
    '/api/admin/reports/count?status=PENDING'
  );
  return res.data;
}

export async function resolveReport(
  id: number,
  action: 'BLOCK_POST' | 'BLOCK_COMMENT' | 'DISMISS',
  reason?: string
): Promise<void> {
  await api.post(`/api/admin/reports/${id}/resolve`, { action, reason });
}
