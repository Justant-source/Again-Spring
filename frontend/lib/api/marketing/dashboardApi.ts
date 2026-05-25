import { api } from '../client';

export interface PlatformStat {
  platform: string;
  publishedCount: number;
  impressions: number;
  likes: number;
  comments: number;
}

export interface CalendarItem {
  id: number;
  platform: string;
  status: string;
  scheduledAt: string | null;
  publishedAt: string | null;
  title: string | null;
}

export interface DashboardSummary {
  weeklyPublished: number;
  cumulativeImpressions: number;
  averageEngagementRate: number;
  weeklyCostUsd: number;
  platformStats: PlatformStat[];
  upcomingPublishes: CalendarItem[];
}

export interface WeeklyTrendItem {
  weekLabel: string;
  costUsd: number;
  count: number;
}

export async function getDashboardSummary(): Promise<DashboardSummary> {
  const res = await api.get<DashboardSummary>('/api/admin/marketing/dashboard/summary');
  return res.data;
}

export async function getWeeklyTrend(): Promise<WeeklyTrendItem[]> {
  const res = await api.get<WeeklyTrendItem[]>('/api/admin/marketing/dashboard/trend');
  return res.data;
}
