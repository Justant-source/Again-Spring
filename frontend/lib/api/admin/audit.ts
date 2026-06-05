import { api } from '../client';
import type { PageResponse } from '.';

export interface AdminAuditLogResponse {
  id: number;
  actorUserId: string;
  action: string;
  targetType?: string;
  targetId?: string;
  beforeJson?: string;
  afterJson?: string;
  ip?: string;
  createdAt: string;
}

export interface AuditLogParams {
  actor?: string;
  action?: string;
  targetType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

/**
 * 감사 로그 목록 조회 (페이지네이션 지원)
 */
export async function listAuditLogs(
  params: AuditLogParams = {}
): Promise<PageResponse<AdminAuditLogResponse>> {
  const res = await api.get<PageResponse<AdminAuditLogResponse>>('/api/admin/audit', {
    params,
  });
  return res.data;
}
