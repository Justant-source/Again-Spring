'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { CommunicationStyle, MbtiProfile, User } from '@/lib/types';

// 카카오톡 인앱 등 localStorage 제한 환경에서 setItem/removeItem이 throw할 수 있으므로
// 모든 접근을 try-catch로 감싼 어댑터를 사용한다.
const safeStorage = {
  getItem: (name: string): string | null => {
    try { return localStorage.getItem(name); } catch { return null; }
  },
  setItem: (name: string, value: string): void => {
    try { localStorage.setItem(name, value); } catch { /* noop */ }
  },
  removeItem: (name: string): void => {
    try { localStorage.removeItem(name); } catch { /* noop */ }
  },
};

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
  setTutorialCompleted: () => void;
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
      setTutorialCompleted: () =>
        set((prev) =>
          prev.user ? { user: { ...prev.user, tutorialCompleted: true } } : prev,
        ),
      clear: () => {
        if (typeof window !== 'undefined') {
          try { localStorage.removeItem('again-spring-token'); } catch { /* noop */ }
        }
        return set({ user: null });
      },
    }),
    {
      name: 'again-spring-user',
      storage: createJSONStorage(() => safeStorage as unknown as Storage),
      onRehydrateStorage: () => (state) => {
        state?.setHydrated(true);
      },
    },
  ),
);

export const useHasHydrated = () => useUserStore((s) => s._hasHydrated);
