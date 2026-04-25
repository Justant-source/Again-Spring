// Source: /home/justant/Data/Again-Spring/frontend/lib/types/user.ts
// Copied for self-contained shared types; keep in sync with frontend canonical

export type CommunicationStyle =
  | 'wave'
  | 'mountain'
  | 'flame'
  | 'leaf'
  | 'moon'
  | 'star';

export interface User {
  id: string;
  email?: string;
  nickname: string;
  isGuest: boolean;
  communicationStyle?: CommunicationStyle;
  onboardingAnswers?: number[];
  createdAt: string;
}
