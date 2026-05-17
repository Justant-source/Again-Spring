'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useUiStore } from '@/lib/store/uiStore';
import { useUserStore } from '@/lib/store/userStore';

/**
 * 401/403 auth errors를 감지해 적절한 페이지로 리다이렉트.
 * api/client.ts interceptor가 store에 신호만 보내고,
 * 실제 navigation은 이 훅(React 레이어)이 담당한다.
 */
export function useAuthRedirect() {
  const router = useRouter();
  const authError = useUiStore(s => s.authError);
  const clearAuthError = useUiStore(s => s.clearAuthError);
  const user = useUserStore(s => s.user);

  useEffect(() => {
    if (!authError) return;
    clearAuthError();
    const isGuest = user?.isGuest === true;
    router.replace(isGuest ? '/guest' : '/login');
  }, [authError, clearAuthError, router, user]);
}
