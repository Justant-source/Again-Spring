'use client';

import { useEffect } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import { recordVisit } from '@/lib/api/visits';

const VISITOR_COOKIE = 'as_vid';
const UTM_COOKIE = 'as_utm';
const VISITOR_TTL_DAYS = 365;
const UTM_TTL_DAYS = 30;

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

function writeCookie(name: string, value: string, days: number) {
  const expires = new Date(Date.now() + days * 24 * 60 * 60 * 1000).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`;
}

function randomKey(): string {
  // crypto.randomUUID는 비보안 컨텍스트(카카오톡 인앱 http)에서 undefined다.
  // 계측이 그런 환경에서만 조용히 죽는 일이 없도록 폴백을 둔다.
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID().replace(/-/g, '').slice(0, 32);
    }
  } catch {
    /* fall through */
  }
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 12)}`;
}

/**
 * 방문 계측.
 *
 * 2026-08-29 개편 — 이전에는 UTM이나 외부 referrer가 있을 때만 기록했다. 그래서
 * "사이트에 사람이 몇 명 왔나"라는 가장 기본적인 질문에 답할 수 없었고, 마케팅 개선의
 * 효과를 잴 분모가 없었다. 이제 모든 페이지뷰를 남기고, 봇 판정은 서버가 User-Agent로
 * 한 뒤 is_bot 플래그로 구분한다(집계에서 제외하되 행은 보존).
 *
 * - visitorKey: 1년 쿠키. 고유 방문자·재방문 판별용.
 * - sessionKey: sessionStorage. 한 세션 안의 이동을 묶는다.
 * - as_utm: first-touch UTM 30일 쿠키. 가입 시 백엔드가 읽어 채널을 귀속한다.
 */
export function VisitTracker() {
  const pathname = usePathname();
  const searchParams = useSearchParams();

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (pathname?.startsWith('/admin')) return;

    // 같은 세션에서 같은 경로를 반복 기록하지 않는다 (뒤로가기·리렌더 중복 방지).
    const storageKey = `as_visit_${pathname}`;
    try {
      if (sessionStorage.getItem(storageKey)) return;
      sessionStorage.setItem(storageKey, '1');
    } catch {
      // 사파리 프라이빗 모드 등에서 sessionStorage가 던진다. 중복을 감수하고 계속 기록한다.
    }

    const utmSource = searchParams.get('utm_source') || undefined;
    const utmMedium = searchParams.get('utm_medium') || undefined;
    const utmCampaign = searchParams.get('utm_campaign') || undefined;
    const utmContent = searchParams.get('utm_content') || undefined;
    const referrer = document.referrer || undefined;
    const hasUtm = !!(utmSource || utmMedium || utmCampaign);

    // first-touch 정책 — 이미 있으면 덮어쓰지 않는다. 마지막 클릭이 아니라
    // "처음 데려온 채널"을 가입에 귀속시키기 위해서다.
    if (hasUtm && !readCookie(UTM_COOKIE)) {
      writeCookie(
        UTM_COOKIE,
        JSON.stringify({ source: utmSource, medium: utmMedium, campaign: utmCampaign }),
        UTM_TTL_DAYS,
      );
    }

    let visitorKey = readCookie(VISITOR_COOKIE);
    if (!visitorKey) {
      visitorKey = randomKey();
    }
    // 방문할 때마다 만료를 연장한다(rolling) — 활성 방문자가 신규로 잘못 세지지 않게.
    writeCookie(VISITOR_COOKIE, visitorKey, VISITOR_TTL_DAYS);

    let sessionKey: string | null = null;
    try {
      sessionKey = sessionStorage.getItem('as_session_key');
      if (!sessionKey) {
        sessionKey = randomKey();
        sessionStorage.setItem('as_session_key', sessionKey);
      }
    } catch {
      sessionKey = randomKey();
    }

    recordVisit({
      path: pathname,
      utmSource,
      utmMedium,
      utmCampaign,
      utmContent,
      referrer,
      sessionKey: sessionKey ?? undefined,
      visitorKey,
    }).catch(() => {});
  }, [pathname, searchParams]);

  return null;
}
