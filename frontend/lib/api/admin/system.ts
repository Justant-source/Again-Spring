import { api } from '../client';

/**
 * 시스템 건강 상태 재로드 요청
 */
export async function reloadPrompts(): Promise<{ message: string }> {
  const res = await api.post<{ message: string }>('/api/admin/prompts/reload');
  return res.data;
}
