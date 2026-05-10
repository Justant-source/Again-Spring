import axios from 'axios';
import { useUiStore } from '@/lib/store/uiStore';

export const api = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('again-spring-token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status;
    const code = error.response?.data?.error?.code;

    if (status === 401 || status === 403) {
      if (typeof window !== 'undefined') {
        const isGuest = (() => {
          try {
            const raw = localStorage.getItem('again-spring-user');
            if (!raw) return false;
            const parsed = JSON.parse(raw);
            return parsed?.state?.user?.isGuest === true;
          } catch { return false; }
        })();
        localStorage.removeItem('again-spring-token');
        window.location.href = isGuest ? '/guest' : '/login';
      }
      return Promise.reject(error);
    }

    if (status === 402 && code === 'GUEST_LIMIT_REACHED') {
      const match = typeof window !== 'undefined'
        ? window.location.pathname.match(/\/session\/chat\/([^/]+)/)
        : null;
      const sessionId = match ? match[1] : null;
      useUiStore.getState().showGuestLimitModal(sessionId);
    } else if (status === 429 && code === 'DAILY_LIMIT_EXCEEDED') {
      useUiStore.getState().showDailyLimitModal();
    }
    return Promise.reject(error);
  },
);

export interface CreateSessionPayload {
  relationType: string;
  category: { majorId: string; middleId: string; minorId: string; customText?: string };
  description: string;
}

export interface TurnRequest {
  sessionId: string;
  turnNumber: number;
  role: 'A' | 'B';
  content: string;
}
