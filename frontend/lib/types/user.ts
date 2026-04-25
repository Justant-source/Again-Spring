export type CommunicationStyle =
  | 'wave'
  | 'mountain'
  | 'flame'
  | 'leaf'
  | 'moon'
  | 'star';

export interface TemperatureEntry {
  sessionId: string;
  partnerId: string;
  temperature: number;
  recordedAt: string;
}

export interface User {
  id: string;
  email?: string;
  nickname: string;
  isGuest: boolean;
  communicationStyle?: CommunicationStyle;
  onboardingAnswers?: number[];
  onboardingCompletedAt?: string | null;
  temperatureHistory: TemperatureEntry[];
  createdAt: string;
}
