import axios from 'axios';
import { useUiStore } from '@/lib/store/uiStore';
import { useUserStore } from '@/lib/store/userStore';
import { ensureGuestToken } from '@/lib/api/guestAuth';

export const api = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    try {
      const token = localStorage.getItem('again-spring-token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch { /* localStorage 차단 환경 (카카오톡 인앱 등) — 인증 헤더 없이 계속 */ }
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const status = error.response?.status;
    const code = error.response?.data?.error?.code;
    const url = error.config?.url || '';

    // community 쓰기(투표·댓글·좋아요)에서 401/403 = 게스트 토큰 누락/만료/거부.
    // 회원이 아니면 게스트 토큰을 재발급하고 원요청을 1회 재시도 → 자동 복구.
    // (페이지 로드 직후 발급 전 클릭하는 레이스, 토큰 만료, stale 상태 모두 커버)
    const member = useUserStore.getState().user?.isGuest === false;
    if (
      (status === 401 || status === 403) &&
      url.includes('/api/community/') &&
      !member &&
      error.config &&
      !error.config._guestRetried
    ) {
      try {
        error.config._guestRetried = true;
        const token = await ensureGuestToken(true); // force=true: 거부된 토큰 강제 재발급
        error.config.headers = error.config.headers || {};
        error.config.headers.Authorization = `Bearer ${token}`;
        return api.request(error.config);
      } catch {
        // 재발급 실패 — 아래 일반 처리로 진행
      }
    }

    if (status === 401) {
      // community 엔드포인트의 401은 토큰 삭제하지 않음 (게스트 처리용)
      if (!url.includes('/api/community/')) {
        if (typeof window !== 'undefined') {
          try { localStorage.removeItem('again-spring-token'); } catch { /* noop */ }
        }
        useUiStore.getState().setAuthError('unauthorized');
      }
    } else if (status === 402 && code === 'GUEST_LIMIT_REACHED') {
      const match = typeof window !== 'undefined'
        ? window.location.pathname.match(/\/session\/chat\/([^/]+)/)
        : null;
      useUiStore.getState().showGuestLimitModal(match ? match[1] : null);
    } else if (status === 429 && code === 'DAILY_LIMIT_EXCEEDED') {
      useUiStore.getState().showDailyLimitModal();
    }

    return Promise.reject(error);
  },
);

export interface TurnRequest {
  sessionId: string;
  turnNumber: number;
  role: 'A' | 'B';
  content: string;
}
