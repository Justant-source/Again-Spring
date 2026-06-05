'use client';

import { useEffect } from 'react';
import { api } from '@/lib/api/client';
import { useUserStore } from '@/lib/store/userStore';
import { getOrCreateDeviceId } from '@/lib/utils/deviceId';

function isJwtExpired(token: string): boolean {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return typeof payload.exp === 'number' && payload.exp * 1000 < Date.now();
  } catch {
    return false;
  }
}

interface GuestAuthResponse {
  user: {
    id: string;
    nickname: string;
    isGuest: boolean;
  };
  token: {
    accessToken: string;
    expiresIn: number;
  };
}

async function initGuest(setUser: ReturnType<typeof useUserStore.getState>['setUser']) {
  const deviceId = getOrCreateDeviceId();

  // nickname은 보내지 않음 — 백엔드가 유니크 검증된 닉네임을 생성/유지
  // (deviceId 기준 동일 기기 = 동일 게스트 계정 재사용, 닉네임도 보존됨)
  const res = await api.post<GuestAuthResponse>('/api/auth/guest', {
    deviceId,
  });

  const { user, token } = res.data;
  if (token?.accessToken) {
    localStorage.setItem('again-spring-token', token.accessToken);
  }
  if (user) {
    setUser({
      id: user.id,
      nickname: user.nickname,
      isGuest: true,
      createdAt: new Date().toISOString(),
    });
  }
}

export function useGuestInit() {
  const user = useUserStore((s) => s.user);
  const hasHydrated = useUserStore((s) => s._hasHydrated);
  const setUser = useUserStore((s) => s.setUser);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (!hasHydrated) return;

    // 이미 로그인된 회원이면 스킵
    if (user && !user.isGuest) return;

    const token = localStorage.getItem('again-spring-token');

    // 토큰이 있으면 AuthBootstrap의 /api/users/me가 처리 — 여기서는 건드리지 않음
    // (token && !user 상태에서 게스트를 재발급하면 회원 토큰을 덮어쓰는 race condition 발생)
    // 단, 게스트 토큰이 만료됐으면 삭제 후 재발급
    if (token && !isJwtExpired(token)) return;
    if (token && isJwtExpired(token)) {
      localStorage.removeItem('again-spring-token');
    }

    // 토큰도 없고 user도 없는 완전 신규 방문자만 자동 게스트 발급
    if (!user) {
      initGuest(setUser).catch((err) => console.error('Guest init failed:', err));
    }
  }, [hasHydrated, user, setUser]);
}
