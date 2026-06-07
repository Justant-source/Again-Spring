import { api } from '../client';

export async function reloadPrompts(): Promise<{ message: string }> {
  const res = await api.post<{ message: string }>('/api/admin/prompts/reload');
  return res.data;
}

export interface SystemLogEntry {
  timestamp: string;
  level: 'ERROR' | 'WARN';
  logger: string;
  message: string;
  exception?: string | null;
}

export async function getSystemLogs(level?: 'ERROR' | 'WARN', limit = 100): Promise<SystemLogEntry[]> {
  const params: Record<string, string | number> = { limit };
  if (level) params.level = level;
  const res = await api.get<SystemLogEntry[]>('/api/admin/system/logs', { params });
  return res.data;
}
