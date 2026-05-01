'use client';

import { useEffect } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import type { User } from '@/lib/types';

/**
 * Restores user session from token if zustand store is empty but a token exists.
 * Mounts once in layout.tsx.
 */
export function AuthBootstrap() {
  const user = useUserStore((s) => s.user);
  const hasHydrated = useUserStore((s) => s._hasHydrated);
  const setUser = useUserStore((s) => s.setUser);
  const clear = useUserStore((s) => s.clear);

  useEffect(() => {
    if (!hasHydrated) return;
    if (user) return;

    const token =
      typeof window !== 'undefined'
        ? localStorage.getItem('again-spring-token')
        : null;
    if (!token) return;

    api.get<User>('/api/users/me').then((res) => {
      setUser(res.data);
    }).catch(() => {
      // Token invalid or expired — clean up
      clear();
    });
  }, [hasHydrated, user, setUser, clear]);

  return null;
}
