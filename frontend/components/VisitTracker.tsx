'use client';

import { useEffect } from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import { recordVisit } from '@/lib/api/visits';

export function VisitTracker() {
  const pathname = usePathname();
  const searchParams = useSearchParams();

  useEffect(() => {
    // 어드민 경로 제외
    if (pathname?.startsWith('/admin')) return;

    // sessionStorage 중복 방지
    const storageKey = `as_visit_${pathname}`;
    if (sessionStorage.getItem(storageKey)) return;

    const utmSource = searchParams.get('utm_source') || undefined;
    const utmMedium = searchParams.get('utm_medium') || undefined;
    const utmCampaign = searchParams.get('utm_campaign') || undefined;
    const utmContent = searchParams.get('utm_content') || undefined;
    const referrer = document.referrer || undefined;

    // utm 또는 외부 referrer가 있을 때만 기록
    const hasUtm = !!(utmSource || utmMedium || utmCampaign);
    const hasExternalReferrer = referrer && !referrer.includes('againspring.net');

    if (!hasUtm && !hasExternalReferrer) return;

    sessionStorage.setItem(storageKey, '1');

    // first-touch utm을 쿠키에 저장 (30일, 이미 있으면 덮어쓰지 않음)
    if (hasUtm && !document.cookie.includes('as_utm=')) {
      const utmData = JSON.stringify({ source: utmSource, medium: utmMedium, campaign: utmCampaign });
      const expires = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toUTCString();
      document.cookie = `as_utm=${encodeURIComponent(utmData)}; expires=${expires}; path=/; SameSite=Lax`;
    }

    // session key 생성 (또는 기존 사용)
    let sessionKey = sessionStorage.getItem('as_session_key');
    if (!sessionKey) {
      sessionKey = Math.random().toString(36).substring(2, 15);
      sessionStorage.setItem('as_session_key', sessionKey);
    }

    recordVisit({
      path: pathname,
      utmSource,
      utmMedium,
      utmCampaign,
      utmContent,
      referrer,
      sessionKey,
    }).catch(() => {}); // fire-and-forget

  }, [pathname, searchParams]);

  return null;
}
