'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

// Legacy store — mediator code removed
export interface ActiveSessionCategory {
  majorId: string;
  customText?: string;
}

interface SessionState {
  sessionId: string | null;
  inviteToken: string | null;
  category: ActiveSessionCategory | null;
  description: string;
  status: string;

  setCategory: (c: ActiveSessionCategory) => void;
  setDescription: (text: string) => void;
  setSession: (s: { id: string; inviteToken: string }) => void;
  reset: () => void;
}

const initial = {
  sessionId: null,
  inviteToken: null,
  category: null,
  description: '',
  status: 'chatting_solo',
};

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      ...initial,
      setCategory: (c) => set({ category: c }),
      setDescription: (text) => set({ description: text }),
      setSession: ({ id, inviteToken }) =>
        set({ sessionId: id, inviteToken }),
      reset: () => set(initial),
    }),
    {
      name: 'again-spring-session',
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
