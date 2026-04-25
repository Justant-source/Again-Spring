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
  onboardingCompletedAt?: string | null;
  createdAt: string;
}
