/**
 * /community (광장) — 서버 컴포넌트 래퍼.
 *
 * 이전에는 파일 전체가 'use client' + useEffect 페칭이었다. SSR 시점엔 posts=[]라서
 * 크롤러가 받는 HTML은 실제 사연 목록 대신 "아직 사연이 없습니다" 빈 상태 문구였다
 * (완전한 CSR bailout은 아니지만 결과적으로 검색엔진에 콘텐츠가 전혀 안 잡히는 건 같다).
 *
 * 기본 필터(전체 카테고리·최신순) 1페이지(20건)만 서버에서 fetch해
 * CommunityFeedClient에 initial prop으로 넘긴다. 카테고리 필터·정렬 전환·무한스크롤·투표는
 * 기존 그대로 클라이언트가 담당 — 전면 재작성이 아니라 초기 데이터 주입이다.
 *
 * 로그인 사용자별 데이터(내 투표 여부 myVoteSide 등)는 서버 fetch에 인증 토큰을 싣지 않으므로
 * 항상 비어 있다 — voteStore(localStorage 기반)가 클라이언트에서 채운다.
 * 사연 상세(/community/[id])의 generateMetadata + opengraph-image.tsx는 이미 잘 되어 있어 손대지 않는다.
 */
import type { Metadata } from 'next';
import CommunityFeedClient from './CommunityFeedClient';
import type { PostSummary } from '@/lib/api/community/postApi';
import { SERVER_API_BASE as API_BASE } from '@/lib/serverApiBase';

// app/sitemap.ts와 동일한 패턴 — 서버 컴포넌트는 next.config.mjs의 /api rewrite를 타지 않으므로
// API_BASE_URL을 직접 사용해야 한다.


// 광장은 홈보다 갱신 빈도가 잦다(새 사연·투표·댓글이 계속 쌓임) — 권장 구간(60~300초) 하단에 가깝게.
// 🔴 정적 프리렌더 금지 — 요청 시 렌더한다.
//
// 2026-08-29: ISR(revalidate=90)로 두었더니 광장 HTML이 빈 채로 배포됐다.
// 원인은 구조적이다 — 정적 프리렌더는 `docker build` 중에 실행되는데 그 시점에는
// backend 컨테이너가 아직 없어 fetch가 반드시 실패한다. 그래서 "아직 사연이
// 없습니다"가 그대로 구워졌고, ISR 재검증도 이를 되돌리지 못했다(x-nextjs-cache: HIT).
//
// 크롤러가 배포 직후 한 번 들르고 마는 상황에서 "몇 번 요청하면 채워진다"는
// 자기치유는 SEO 목적에 맞지 않는다. 백엔드 응답이 0.1초대라 요청마다 렌더해도
// 비용이 무시할 수준이고, 무엇보다 항상 옳다. 트래픽이 커지면 그때 캐시를 다시 건다.
export const dynamic = 'force-dynamic';

// layout.tsx 기본 메타(홈 전용, canonical '/')와 겹치지 않도록 목록 페이지 전용 title/description.
// title은 layout의 template('%s · 다시봄')이 자동으로 '· 다시봄'을 붙이므로 여기서는 중복 표기하지 않는다.
export async function generateMetadata(): Promise<Metadata> {
  return {
    title: '광장 — 갈등 사연 모아보기',
    description:
      '연인·부부·친구·가족·직장에서 생긴 갈등 사연을 모았습니다. 작성자와 상대방 중 '
      + '어느 쪽에 공감하는지 투표하고 다른 사람들의 생각도 확인해보세요.',
    alternates: { canonical: '/community' },
    openGraph: {
      title: '다시봄 광장 — 갈등 사연 모아보기',
      description: '이 갈등, 당신은 어느 쪽에 공감하나요? 지금 올라온 사연들을 둘러보세요.',
      url: '/community',
      type: 'website',
    },
  };
}

async function fetchInitialPosts(): Promise<{ content: PostSummary[]; totalPages: number }> {
  try {
    const res = await fetch(
      `${API_BASE}/api/community/posts?page=0&size=20&sortBy=latest`,
      {
        headers: { Accept: 'application/json' },
        cache: 'no-store',
        signal: AbortSignal.timeout(2500),
      },
    );
    if (!res.ok) return { content: [], totalPages: 0 };
    const body = await res.json();
    return {
      content: Array.isArray(body?.content) ? body.content : [],
      totalPages: typeof body?.totalPages === 'number' ? body.totalPages : 0,
    };
  } catch {
    // 광장 렌더가 BE 장애로 막히면 안 된다 — 빈 배열로 폴백, 클라이언트가 재시도한다.
    return { content: [], totalPages: 0 };
  }
}

export default async function Page() {
  const { content, totalPages } = await fetchInitialPosts();
  return <CommunityFeedClient initialPosts={content} initialHasMore={totalPages > 1} />;
}
