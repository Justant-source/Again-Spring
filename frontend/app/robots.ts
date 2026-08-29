import type { MetadataRoute } from 'next';

const SITE_URL = process.env.NEXT_PUBLIC_APP_URL || 'https://againspring.net';

/**
 * 2026-08-29 신설. 이전까지 /robots.txt는 Cloudflare 기본 주석만 응답했고
 * sitemap.xml은 404였다 — 크롤러에게 무엇을 읽으라고 알려주는 신호가 아예 없었다.
 *
 * dev.againspring.net은 prod와 같은 이미지를 쓰므로, 색인 대상을 도메인으로 가른다.
 * dev가 색인되면 prod와 중복 콘텐츠가 되어 양쪽 순위가 함께 내려간다.
 */
export default function robots(): MetadataRoute.Robots {
  const isProd = SITE_URL.includes('againspring.net') && !SITE_URL.includes('dev.');

  if (!isProd) {
    return { rules: { userAgent: '*', disallow: '/' } };
  }

  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/admin', '/admin/', '/api/', '/auth/', '/s/'],
      },
    ],
    sitemap: `${SITE_URL}/sitemap.xml`,
    host: SITE_URL,
  };
}
