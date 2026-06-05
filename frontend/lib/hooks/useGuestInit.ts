'use client';

import { useEffect } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { ensureGuestToken, getValidToken } from '@/lib/api/guestAuth';

/**
 * 비회원 방문자에게 유효한 게스트 토큰을 보장한다.
 *
 * 유효 토큰이 없으면(미발급·만료·구버전 stale 상태 포함) 게스트를 재발급한다.
 * 발급/복구 책임은 ensureGuestToken에 위임 — useGuestInit은 "회원이면 건너뛴다"만 판단.
 */
export function useGuestInit() {
  const user = useUserStore((s) => s.user);
  const hasHydrated = useUserStore((s) => s._hasHydrated);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (!hasHydrated) return;

    // 이미 로그인된 회원이면 스킵 (게스트 토큰 발급 금지)
    if (user && !user.isGuest) return;

    // 유효한 토큰이 있으면 그대로 사용
    if (getValidToken()) return;

    // 유효 토큰 없음 — 게스트 재발급 (store에 stale user가 남아있어도 재인증)
    ensureGuestToken().catch((err) => console.error('Guest init failed:', err));
  }, [hasHydrated, user]);
}
