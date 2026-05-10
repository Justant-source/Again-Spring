'use client';

import { useEffect, useRef } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import type { User } from '@/lib/types';

/**
 * Restores user session from token if zustand store is empty but a token exists.
 * Mounts once in layout.tsx.
 */
export function AuthBootstrap() {
  const hasHydrated = useUserStore((s) => s._hasHydrated);
  const ranRef = useRef(false);

  useEffect(() => {
    if (!hasHydrated || ranRef.current) return;
    ranRef.current = true;

    const { user, setUser, clear } = useUserStore.getState();

    const token =
      typeof window !== 'undefined'
        ? localStorage.getItem('again-spring-token')
        : null;

    // 토큰이 없는데 user가 store에 남아 있으면 stale → 정리
    if (!token) {
      if (user) clear();
      return;
    }

    // 토큰 유효성 + 최신 동의 상태 동기화 (stale user 방지)
    api.get<User>('/api/users/me').then((res) => {
      setUser(res.data);
    }).catch(() => {
      clear();
    });
  }, [hasHydrated]);

  return null;
}
