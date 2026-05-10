export type CommunicationStyle =
  | 'wave'
  | 'mountain'
  | 'flame'
  | 'leaf'
  | 'moon'
  | 'star';

export interface MbtiProfile {
  e_i: number; // 0=E(외향), 100=I(내향)
  s_n: number; // 0=S(감각), 100=N(직관)
  t_f: number; // 0=T(사고), 100=F(감정)
  j_p: number; // 0=J(판단), 100=P(인식)
}

export interface User {
  id: string;
  email?: string;
  nickname: string;
  isGuest: boolean;
  mustChangePassword?: boolean;
  communicationStyle?: CommunicationStyle;
  onboardingAnswers?: number[];
  onboardingCompletedAt?: string | null;
  mbtiType?: string;
  mbtiProfile?: MbtiProfile;
  onboardingMethod?: 'test' | 'mbti';
  provider?: string | null;
  roles?: string[];
  createdAt: string;
  termsAgreedAt?: string | null;
  privacyAgreedAt?: string | null;
  disclaimerAgreedAt?: string | null;
  marketingAgreedAt?: string | null;
}
