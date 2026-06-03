'use client';

import { useEffect } from 'react';
import { api } from '@/lib/api/client';
import { useUserStore } from '@/lib/store/userStore';
import { getOrCreateDeviceId, deviceToGuestNickname } from '@/lib/utils/deviceId';

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
  const nickname = deviceToGuestNickname(deviceId);

  const res = await api.post<GuestAuthResponse>('/api/auth/guest', {
    nickname,
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
    if (token) return;

    // 토큰도 없고 user도 없는 완전 신규 방문자만 자동 게스트 발급
    if (!user) {
      initGuest(setUser).catch((err) => console.error('Guest init failed:', err));
    }
  }, [hasHydrated, user, setUser]);
}
