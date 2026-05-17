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

    if (status === 401) {
      if (typeof window !== 'undefined') localStorage.removeItem('again-spring-token');
      useUiStore.getState().setAuthError('unauthorized');
    } else if (status === 403) {
      if (typeof window !== 'undefined') localStorage.removeItem('again-spring-token');
      useUiStore.getState().setAuthError('forbidden');
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
