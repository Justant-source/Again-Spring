import axios from 'axios';

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
