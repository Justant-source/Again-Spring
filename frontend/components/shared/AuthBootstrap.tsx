'use client';

import { useEffect, useRef } from 'react';
import axios from 'axios';
import { useUserStore } from '@/lib/store/userStore';
import { isJwtExpired, isGuestToken } from '@/lib/api/guestAuth';
import type { User } from '@/lib/types';

/**
 * Restores user session from token if zustand store is empty but a token exists.
 * Mounts once in layout.tsx.
 *
 * 게스트는 토큰이 없거나 만료돼도 로그아웃·리다이렉트하지 않는다 — useGuestInit/ensureGuestToken이
 * 조용히 재발급한다. /api/users/me를 만료 토큰으로 호출해 401→/guest 리다이렉트가 발생하던
 * 문제를 막기 위해, 만료/누락 토큰일 때는 여기서 회원만 정리하고 게스트는 손대지 않는다.
 */
export function AuthBootstrap() {
  const hasHydrated = useUserStore((s) => s._hasHydrated);
  const ranRef = useRef(false);

  useEffect(() => {
    if (!hasHydrated || ranRef.current) return;
    ranRef.current = true;

    const { user, setUser, clear } = useUserStore.getState();

    let token: string | null = null;
    try {
      token = typeof window !== 'undefined' ? localStorage.getItem('again-spring-token') : null;
    } catch { /* localStorage 제한 환경 */ }

    const isMember = !!user && !user.isGuest;

    // 토큰이 없거나 만료됨
    if (!token || isJwtExpired(token)) {
      // 회원이면 정리 → 회원 흐름이 재로그인 유도. 게스트는 useGuestInit이 재발급하므로 손대지 않음.
      if (isMember) clear();
      return;
    }

    // 게스트 토큰은 /me 동기화 불필요 — 게스트 흐름(useGuestInit·인터셉터)이 관리한다.
    // 특히 삭제된 게스트의 유효 토큰으로 /me를 치면 401→/guest 리다이렉트가 발생하므로 건너뛴다.
    if (isGuestToken(token)) return;

    // 유효한 회원 토큰 — 최신 동의/프로필 상태 동기화 (stale user 방지)
    // raw axios 사용: api 인터셉터가 401→authError→/login 리다이렉트를 유발하지 않도록.
    // 사연 상세 등 공개 페이지에서 stale 토큰으로 인한 불필요한 로그인 리다이렉트 방지.
    axios.get<User>('/api/users/me', {
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      setUser(res.data);
    }).catch(() => {
      if (isMember) clear();
    });
  }, [hasHydrated]);

  return null;
}
