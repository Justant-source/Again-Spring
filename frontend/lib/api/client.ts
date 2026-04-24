import axios from 'axios';

export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
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
