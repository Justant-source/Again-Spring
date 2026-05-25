import { api } from '../client';

export interface Hashtag {
  id: number;
  platform: string;
  tag: string;
  category?: string;
  usageCount: number;
  lastUsedAt?: string;
  createdAt: string;
}

export interface HashtagRequest {
  platform: string;
  tag: string;
  category?: string;
}

export async function getHashtags(platform?: string): Promise<Hashtag[]> {
  const query = platform ? `?platform=${platform}` : '';
  const res = await api.get<Hashtag[]>(`/api/admin/marketing/hashtags${query}`);
  return res.data;
}

export async function createHashtag(data: HashtagRequest): Promise<Hashtag> {
  const res = await api.post<Hashtag>('/api/admin/marketing/hashtags', data);
  return res.data;
}

export async function deleteHashtag(id: number): Promise<void> {
  await api.delete(`/api/admin/marketing/hashtags/${id}`);
}
