import { api } from '../client';

export interface DailyStats {
  date: string;
  count: number;
  costUsd: number;
}

export interface MonthlyStats {
  month: string;
  count: number;
  costUsd: number;
}

export async function getDailyStats(date?: string): Promise<DailyStats> {
  const res = await api.get<DailyStats>('/api/admin/marketing/cost/daily', {
    params: date ? { date } : undefined,
  });
  return res.data;
}

export async function getMonthlyStats(month?: string): Promise<MonthlyStats> {
  const res = await api.get<MonthlyStats>('/api/admin/marketing/cost/monthly', {
    params: month ? { month } : undefined,
  });
  return res.data;
}
