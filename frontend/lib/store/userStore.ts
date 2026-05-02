'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { CommunicationStyle, MbtiProfile, User } from '@/lib/types';

interface UserState {
  user: User | null;
  _hasHydrated: boolean;
  setHydrated: (v: boolean) => void;
  setUser: (u: User) => void;
  setStyle: (s: CommunicationStyle) => void;
  setOnboardingAnswers: (a: number[]) => void;
  setOnboardingCompleted: () => void;
  setMbtiType: (t: string) => void;
  setMbtiProfile: (profile: MbtiProfile) => void;
  setMbtiResult: (t: string, profile?: MbtiProfile) => void;
  clear: () => void;
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      user: null,
      _hasHydrated: false,
      setHydrated: (v) => set({ _hasHydrated: v }),
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
      setMbtiProfile: (profile) =>
        set((prev) =>
          prev.user ? { user: { ...prev.user, mbtiProfile: profile } } : prev,
        ),
      setMbtiResult: (t, profile) =>
        set((prev) =>
          prev.user
            ? {
                user: {
                  ...prev.user,
                  mbtiType: t,
                  mbtiProfile: profile,
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
      onRehydrateStorage: () => (state) => {
        state?.setHydrated(true);
      },
    },
  ),
);

export const useHasHydrated = () => useUserStore((s) => s._hasHydrated);
