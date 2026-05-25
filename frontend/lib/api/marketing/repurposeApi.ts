import { api } from '../client';
import type { ContentResponse } from './contentApi';

export async function repurposeContent(
  sourceId: number,
  targetPlatform: string
): Promise<ContentResponse> {
  const res = await api.post<ContentResponse>(
    `/api/admin/marketing/repurpose/${sourceId}?targetPlatform=${targetPlatform}`
  );
  return res.data;
}
