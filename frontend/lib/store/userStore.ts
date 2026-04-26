'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { CommunicationStyle, User } from '@/lib/types';

interface UserState {
  user: User | null;
  setUser: (u: User) => void;
  setStyle: (s: CommunicationStyle) => void;
  setOnboardingAnswers: (a: number[]) => void;
  setOnboardingCompleted: () => void;
  setMbtiType: (t: string) => void;
  clear: () => void;
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      user: null,
      setUser: (u) => set({ user: u }),
      setStyle: (s) =>
        set((prev) =>
          prev.user
            ? { user: { ...prev.user, communicationStyle: s } }
            : prev,
        ),
      setOnboardingAnswers: (a) =>
        set((prev) =>
          prev.user
            ? { user: { ...prev.user, onboardingAnswers: a } }
            : prev,
        ),
      setOnboardingCompleted: () =>
        set((prev) =>
          prev.user
            ? {
                user: {
                  ...prev.user,
                  onboardingCompletedAt: prev.user.onboardingCompletedAt ?? new Date().toISOString(),
                  onboardingMethod: prev.user.onboardingMethod ?? 'test',
                },
              }
            : prev,
        ),
      setMbtiType: (t) =>
        set((prev) =>
          prev.user
            ? {
                user: {
                  ...prev.user,
                  mbtiType: t,
                  onboardingMethod: 'mbti',
                  onboardingCompletedAt: prev.user.onboardingCompletedAt ?? new Date().toISOString(),
                },
              }
            : prev,
        ),
      clear: () => {
        if (typeof window !== 'undefined') {
          localStorage.removeItem('again-spring-token');
        }
        return set({ user: null });
      },
    }),
    {
      name: 'again-spring-user',
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
