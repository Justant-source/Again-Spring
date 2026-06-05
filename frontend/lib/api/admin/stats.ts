import { api } from '../client';
import type { PageResponse } from '.';

export interface DailyStatsResponse {
  statDate: string;
  dau: number;
  newUsers: number;
  voteCount: number;
  postCount: number;
  feedbackCount: number;
}

/**
 * 일별 통계 조회 (최근 N일)
 */
export async function getDailyStats(days: number = 30): Promise<DailyStatsResponse[]> {
  const res = await api.get<unknown[]>('/api/admin/dashboard/daily-stats');
  return res.data as unknown as DailyStatsResponse[];
}

/**
 * 리텐션 코호트 조회
 */
export async function getRetentionCohort(): Promise<Record<string, unknown>[]> {
  const res = await api.get<Record<string, unknown>[]>('/api/admin/dashboard/retention');
  return res.data;
}

/**
 * 통계 역산 채움 (from ~ to 날짜 범위)
 */
export async function backfillStats(from: string, to: string): Promise<{ message: string }> {
  const res = await api.post<{ message: string }>('/api/admin/dashboard/stats/backfill', null, {
    params: { from, to },
  });
  return res.data;
}

/**
 * 통계를 CSV로 내보내기
 */
export async function exportStatsCSV(days: number = 30): Promise<Blob> {
  const res = await api.get<Blob>('/api/admin/dashboard/daily-stats/export', {
    params: { days },
    responseType: 'blob',
  });
  return res.data;
}
