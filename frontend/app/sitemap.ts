import type { MetadataRoute } from 'next';
import { SERVER_API_BASE as API_BASE } from '@/lib/serverApiBase';

const SITE_URL = process.env.NEXT_PUBLIC_APP_URL || 'https://againspring.net';

// 사연 상세는 검색 유입의 실질적 진입점이다(홈은 CSR이라 크롤러가 볼 내용이 적다).
// 한 번에 너무 많이 넣으면 생성이 느려지므로 최근분 위주로 상한을 둔다.
const PAGE_SIZE = 100;
const MAX_PAGES = 10;

type ListedPost = { id: string; updatedAt?: string; createdAt?: string };

async function fetchPostIds(): Promise<ListedPost[]> {
  const all: ListedPost[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    try {
      const res = await fetch(
        `${API_BASE}/api/community/posts?page=${page}&size=${PAGE_SIZE}&sortBy=latest`,
        { next: { revalidate: 3600 } },
      );
      if (!res.ok) break;
      const body = await res.json();
      const content: ListedPost[] = body?.content ?? [];
      all.push(...content);
      if (content.length < PAGE_SIZE || body?.last === true) break;
    } catch {
      // 사이트맵 생성 실패가 배포를 막아서는 안 된다. 정적 경로만이라도 내보낸다.
      break;
    }
  }
  return all;
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  const staticEntries: MetadataRoute.Sitemap = [
    { url: SITE_URL, lastModified: now, changeFrequency: 'hourly', priority: 1 },
    { url: `${SITE_URL}/community`, lastModified: now, changeFrequency: 'hourly', priority: 0.9 },
    { url: `${SITE_URL}/terms`, lastModified: now, changeFrequency: 'yearly', priority: 0.2 },
    { url: `${SITE_URL}/privacy`, lastModified: now, changeFrequency: 'yearly', priority: 0.2 },
  ];

  const posts = await fetchPostIds();
  const postEntries: MetadataRoute.Sitemap = posts
    .filter((p) => !!p?.id)
    .map((p) => ({
      url: `${SITE_URL}/community/${p.id}`,
      lastModified: p.updatedAt ? new Date(p.updatedAt) : p.createdAt ? new Date(p.createdAt) : now,
      changeFrequency: 'daily' as const,
      priority: 0.8,
    }));

  return [...staticEntries, ...postEntries];
}
