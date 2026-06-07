/**
 * 동적 OG 이미지 — /community/<id>/opengraph-image
 *
 * Next.js App Router 파일 컨벤션(opengraph-image.tsx)으로 og:image / twitter:image 자동 주입.
 * 경로가 /community/ 아래에 있어 nginx가 frontend:3000 으로 라우팅 (※ /api/ 아래 두면 Spring이 받음).
 *
 * runtime = 'nodejs': next start 로 구동되는 Node 컨테이너 환경. edge 금지(fs 미지원).
 */
import { ImageResponse } from 'next/og';
import { fetchPostForOg } from '@/lib/og/fetchPostForOg';
import { loadOgFonts } from '@/lib/og/loadFont';
import { OgCard } from '@/lib/og/OgCard';

export const runtime = 'nodejs';
export const size = { width: 1200, height: 630 };
export const contentType = 'image/png';
export const alt = '다시봄 공감 결과';

export default async function Image({ params }: { params: { id: string } }) {
  // 두 비동기 작업 병렬 실행
  const [postData, fonts] = await Promise.all([
    fetchPostForOg(params.id),
    loadOgFonts(),
  ]);

  // 공개·투표 가능한 글만 실제 카드 — 나머지는 브랜드 fallback
  const cardData = postData.ok && postData.crawlable ? postData : null;

  return new ImageResponse(
    <OgCard data={cardData} />,
    {
      ...size,
      fonts: [
        { name: 'NotoSansKR', data: fonts.regular, weight: 400, style: 'normal' },
        { name: 'NotoSansKR', data: fonts.bold,    weight: 700, style: 'normal' },
      ],
      headers: {
        // 5분 신선도 — 카카오는 스크랩을 한 번 캐시하므로 실시간은 무의미
        // 새 공유(또는 카카오 캐시 만료) 시 최신 투표 비율 반영
        'Cache-Control': 'public, max-age=300, s-maxage=300, stale-while-revalidate=600',
      },
    },
  );
}
