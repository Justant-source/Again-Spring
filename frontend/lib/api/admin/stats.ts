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

// Community Insights API Types
export interface InsightsDto {
  dau: number;
  wau: number;
  mau: number;
  stickiness: number | null;
  funnel: {
    active: number;
    voters: number;
    commenters: number;
    posters: number;
    realUserOnly: boolean;
  };
  contentHealth: {
    avgCommentsPerPost: number;
    noComments24hRate: number; // percent 0~100
  };
  productionSeries: Array<{
    date: string;
    realPosts: number;
    aiPosts: number;
    realComments: number;
    aiComments: number;
  }>;
}

export interface TrafficDto {
  dailySeries: Array<{ date: string; visits: number; uniqueSessions: number }>;
  topSources: Array<{ source: string; visits: number }>;
  topCampaigns: Array<{ campaign: string; visits: number }>;
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

/**
 * 커뮤니티 인사이트 조회
 * GET /api/admin/dashboard/insights?days=30&realOnly=true
 */
export async function getCommunityInsights(days: number = 30, realOnly: boolean = true): Promise<InsightsDto> {
  const res = await api.get<InsightsDto>('/api/admin/dashboard/insights', {
    params: { days, realOnly },
  });
  return res.data;
}

/**
 * 트래픽 요약 조회
 * GET /api/admin/dashboard/traffic?days=30
 */
export async function getTrafficSummary(days: number = 30): Promise<TrafficDto> {
  const res = await api.get<TrafficDto>('/api/admin/dashboard/traffic', {
    params: { days },
  });
  return res.data;
}
