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
    const code = error.response?.data?.error?.code;
    if (error.response?.status === 402 && code === 'GUEST_LIMIT_REACHED') {
      const match = typeof window !== 'undefined'
        ? window.location.pathname.match(/\/session\/chat\/([^/]+)/)
        : null;
      const sessionId = match ? match[1] : null;
      useUiStore.getState().showGuestLimitModal(sessionId);
    } else if (error.response?.status === 429 && code === 'DAILY_LIMIT_EXCEEDED') {
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
