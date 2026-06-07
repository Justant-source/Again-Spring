/**
 * OG 카드용 서버 사이드 post fetch + 정규화.
 * generateMetadata 와 opengraph-image.tsx 가 공유하는 단일 소스.
 *
 * 브라우저 axios(postApi)는 서버에서 사용 불가 → 백엔드 직접 호출.
 * 절대 throw 하지 않음 — 오류는 항상 FALLBACK 반환.
 */
import 'server-only';

// 카테고리 enum → 표시 한글 (app/community/[id]/page.tsx CAT_LABELS 와 동일 셋)
export const OG_CAT_LABELS: Record<string, string> = {
  COUPLE: '연인',
  MARRIED: '부부',
  FRIEND: '친구',
  FAMILY: '가족',
  WORK: '직장',
  OTHER: '기타',
};

export interface OgPostData {
  ok: boolean;
  /** visibility=PUBLIC && status in {VOTING,CLOSED} 일 때 true */
  crawlable: boolean;
  /** 글 제목 (최대 38자 하드 truncate 적용) */
  title: string;
  /** 카테고리 enum (예: 'COUPLE') */
  category: string;
  /** 작성자 공감 비율 0-100 정수. votes==0 이면 50 */
  authorPct: number;
  /** 상대방 공감 비율 = 100 - authorPct (합 100 보장) */
  partnerPct: number;
  /** 전체 투표 참여자 수 */
  totalVotes: number;
}

const FALLBACK: OgPostData = {
  ok: false,
  crawlable: false,
  title: '',
  category: 'OTHER',
  authorPct: 50,
  partnerPct: 50,
  totalVotes: 0,
};

// 런타임 env 우선, compose 미설정 시 기존 API_BASE_URL fallback, 최종 로컬 dev 기본값
const BASE =
  process.env.BACKEND_INTERNAL_URL ||
  process.env.API_BASE_URL ||
  'http://localhost:8080';

/** 길고 불필요한 공백 제거 후 max 자 이내로 자름 */
function clipTitle(s: string, max = 38): string {
  const t = s.trim().replace(/\s+/g, ' ');
  return t.length > max ? t.slice(0, max) + '…' : t;
}

export async function fetchPostForOg(id: string): Promise<OgPostData> {
  try {
    const url = `${BASE}/api/community/posts/${encodeURIComponent(id)}`;
    const res = await fetch(url, {
      headers: { Accept: 'application/json' },
      // Next 14 fetch 캐시: 60초 revalidate (ISR-style)
      next: { revalidate: 60 },
      // 크롤러가 기다리지 않도록 타임아웃 — 초과 시 fallback
      signal: AbortSignal.timeout(2500),
    });

    if (!res.ok) {
      // 403(PRIVATE/DRAFT/BLOCKED), 404(없음), 5xx → fallback
      return FALLBACK;
    }

    const p = await res.json();

    // PRIVATE 글이나 DRAFT/BLOCKED 는 크롤러에 노출하지 않음
    const crawlable =
      p.visibility === 'PUBLIC' &&
      (p.status === 'VOTING' || p.status === 'CLOSED');

    // orderIdx 0 = 작성자(peach), orderIdx 1 = 상대방(sage)
    // label로 찾고 없으면 index fallback
    const opts: Array<{ label: string; percentage: number }> =
      p.voteResult?.options ?? [];
    const authorOpt = opts.find((o) => o.label === '작성자') ?? opts[0];
    const total: number = p.voteResult?.totalVotes ?? 0;

    // 반올림은 작성자만 1회 → 상대방은 100-authorPct 로 도출해 합 100 보장
    const authorPct = total > 0 ? Math.round(authorOpt?.percentage ?? 0) : 50;
    const partnerPct = total > 0 ? 100 - authorPct : 50;

    return {
      ok: true,
      crawlable,
      title: clipTitle(p.title ?? '다시봄 사연'),
      category: p.category ?? 'OTHER',
      authorPct,
      partnerPct,
      totalVotes: total,
    };
  } catch {
    // 네트워크 오류 / 타임아웃 / JSON 파싱 오류 → fallback (절대 throw 금지)
    return FALLBACK;
  }
}
