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

export interface ActionCenterResponse {
  pendingReports: number;
  openInquiries: number;
  marketingAwaitingApproval: number;
  marketingFailed: number;
  aiFailuresToday: number;
  aiBlockedToday: number;
  crisisRecent24h: number;
}

export interface KpiMetricDto {
  key: string;
  label: string;
  value: number;
  delta: number | null;
  deltaPercent: number | null;
  sparkline: number[];
}

export interface PulseSlot {
  hour: number;
  postsReal: number;
  postsAi: number;
  commentsReal: number;
  commentsAi: number;
  votesReal: number;
  votesAi: number;
}

export interface CommunityPulseResponse {
  data: PulseSlot[];
}

export interface HotPostDto {
  id: string;
  title: string;
  synthetic: boolean;
  voteCount: number;
  commentCount: number;
  viewCount: number;
  score: number;
  createdAt: string;
}

/**
 * 대시보드 요약 통계 조회
 */
export async function getDashboardSummary(): Promise<DashboardSummaryResponse> {
  const res = await api.get<DashboardSummaryResponse>('/api/admin/dashboard/summary');
  return res.data;
}

/**
 * Action Center 데이터 조회
 */
export async function getActionCenter(): Promise<ActionCenterResponse> {
  const res = await api.get<ActionCenterResponse>('/api/admin/dashboard/action-center');
  return res.data;
}

/**
 * KPI 메트릭 조회
 */
export async function getKpiMetrics(days?: number): Promise<KpiMetricDto[]> {
  const res = await api.get<KpiMetricDto[]>('/api/admin/dashboard/kpis', {
    params: days ? { days } : undefined,
  });
  return res.data;
}

/**
 * 커뮤니티 펄스 조회
 */
export async function getCommunityPulse(hours?: number): Promise<CommunityPulseResponse> {
  const res = await api.get<CommunityPulseResponse>('/api/admin/dashboard/pulse', {
    params: hours ? { hours } : undefined,
  });
  return res.data;
}

/**
 * 핫 게시글 조회
 */
export async function getHotPosts(hours?: number, limit?: number): Promise<HotPostDto[]> {
  const res = await api.get<HotPostDto[]>('/api/admin/dashboard/hot-posts', {
    params: {
      ...(hours ? { hours } : {}),
      ...(limit ? { limit } : {}),
    },
  });
  return res.data;
}
