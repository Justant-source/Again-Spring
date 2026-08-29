/**
 * / (홈) — 서버 컴포넌트 래퍼.
 *
 * 이전에는 파일 전체가 'use client' + useEffect 페칭이었고, 첫 마운트 전까지
 * `if (!mounted) return null`으로 렌더 자체를 막았다. 그 결과 SSR HTML에는
 * 사연 텍스트가 전혀 없었다(크롤러가 홈에서 아무 콘텐츠도 못 봄 — 검색 유입 0의 원인 중 하나).
 *
 * "방금 올라온 사연"(최신 1건)과 "오늘의 사연"(추천순 상위에서 오늘자 우선 선택)만
 * 서버에서 fetch해 LandingPageClient에 initial prop으로 넘긴다. 필터·재조회 같은
 * 상호작용은 그대로 클라이언트(useEffect)가 담당 — 전면 재작성이 아니라 초기 데이터 주입.
 *
 * 로그인 사용자별 데이터(관리자 진입 버튼, 유저 칩)는 여기서 절대 만들지 않는다.
 * 서버 fetch에는 인증 토큰을 싣지 않으므로 항상 익명 응답이고, 사용자별 UI는
 * LandingPageClient가 mounted 이후 클라이언트에서만 채운다 — 그래야 ISR 캐시가
 * 사용자 간에 섞이지 않는다.
 */
import LandingPageClient from './LandingPageClient';
import type { PostSummary } from '@/lib/api/community/postApi';
import { SERVER_API_BASE as API_BASE } from '@/lib/serverApiBase';

// app/sitemap.ts와 동일한 패턴 — 서버 컴포넌트는 next.config.mjs의 /api rewrite를 타지 않으므로
// API_BASE_URL을 직접 사용해야 한다.


// 사연은 자주 올라오지 않지만 "방금 올라온 사연" 알약은 신선도가 중요하다.
// 매 요청마다 BE를 두드리면 부하만 커지므로 60~300초 권장 구간의 중간값(120초) ISR로 절충한다.
export const revalidate = 120;

async function fetchList(sortBy: 'latest' | 'recommended', size: number): Promise<PostSummary[]> {
  try {
    const res = await fetch(
      `${API_BASE}/api/community/posts?page=0&size=${size}&sortBy=${sortBy}`,
      {
        headers: { Accept: 'application/json' },
        next: { revalidate: 120 },
        signal: AbortSignal.timeout(2500),
      },
    );
    if (!res.ok) return [];
    const body = await res.json();
    return Array.isArray(body?.content) ? body.content : [];
  } catch {
    // 홈 렌더가 BE 장애로 막히면 안 된다 — 빈 배열로 폴백, 클라이언트 useEffect가 재시도한다.
    return [];
  }
}

// ISO 날짜 → KST YYYY-MM-DD (LandingPageClient의 동명 함수와 동일 로직 — 서버/클라이언트 각자 fetch라 공유 불필요)
function kstDate(iso: string) {
  return new Date(iso).toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
}

export default async function Page() {
  const [latestList, recommendedList] = await Promise.all([
    fetchList('latest', 1),
    fetchList('recommended', 20),
  ]);

  const initialLatestPost = latestList[0] ?? null;

  const today = new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Seoul' });
  const initialTodayPost =
    recommendedList.find((p) => kstDate(p.createdAt) === today) ?? recommendedList[0] ?? null;

  return <LandingPageClient initialLatestPost={initialLatestPost} initialTodayPost={initialTodayPost} />;
}
