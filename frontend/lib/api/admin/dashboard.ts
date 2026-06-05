import { api } from '../client';

export interface DashboardSummaryResponse {
  todayNewUsers: number;
  totalUsers: number;
  totalPosts: number;
  totalVotes: number;
  totalComments: number;
  pendingReports: number;
  openInquiries: number;
  todayFeedback: number;
  todayVotes: number;
}

/**
 * 대시보드 요약 통계 조회
 */
export async function getDashboardSummary(): Promise<DashboardSummaryResponse> {
  const res = await api.get<DashboardSummaryResponse>('/api/admin/dashboard/summary');
  return res.data;
}
