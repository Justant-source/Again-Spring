import { api } from '../client';
import type { CalendarItem } from './dashboardApi';

export type { CalendarItem };

export async function getCalendarItems(from: string, to: string): Promise<CalendarItem[]> {
  const res = await api.get<CalendarItem[]>(`/api/admin/marketing/calendar?from=${from}&to=${to}`);
  return res.data;
}
