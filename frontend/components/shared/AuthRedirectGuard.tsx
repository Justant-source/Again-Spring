'use client';

import { useAuthRedirect } from '@/lib/hooks/useAuthRedirect';

/**
 * 401/403 auth 에러를 감지해 리다이렉트하는 단일 경계.
 * app/layout.tsx에 한 번만 마운트.
 */
export function AuthRedirectGuard() {
  useAuthRedirect();
  return null;
}
