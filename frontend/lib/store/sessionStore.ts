'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type {
  RelationType,
  SessionStatus,
} from '@/lib/types';

export interface ActiveSessionCategory {
  majorId: string;
  middleId: string;
  minorId: string;
  customText?: string;
}

// Legacy types for backward compatibility with sessionStore
type ParticipantRole = 'A' | 'B';
interface Turn {
  turnNumber: number;
  role: ParticipantRole;
  content: string;
  mediatorMessage?: string;
  isPerspectiveTaking?: boolean;
  skipped?: boolean;
  createdAt: string;
}

interface SessionState {
  sessionId: string | null;
  inviteToken: string | null;
  relationType: RelationType | null;
  category: ActiveSessionCategory | null;
  description: string;
  role: ParticipantRole | null;
  status: SessionStatus;
  currentTurn: number;
  turns: Turn[];
  inviteMessageTone: 'soft' | 'light' | 'serious';
  partnerNickname?: string;
  soloMode: boolean | null;
  mediatorStyleX: number;
  mediatorStyleY: number;

  setRelationType: (t: RelationType) => void;
  setCategory: (c: ActiveSessionCategory) => void;
  setDescription: (text: string) => void;
  setInviteTone: (tone: 'soft' | 'light' | 'serious') => void;
  setSession: (s: { id: string; inviteToken: string }) => void;
  setStatus: (s: SessionStatus) => void;
  appendTurn: (t: Turn) => void;
  setCurrentTurn: (n: number) => void;
  setRole: (r: ParticipantRole) => void;
  setPartnerNickname: (n: string) => void;
  setSoloMode: (mode: boolean) => void;
  setMediatorStyle: (x: number, y: number) => void;
  reset: () => void;
}

const initial = {
  sessionId: null,
  inviteToken: null,
  relationType: null,
  category: null,
  description: '',
  role: null as ParticipantRole | null,
  status: 'chatting_solo' as SessionStatus,
  currentTurn: 1,
  turns: [] as Turn[],
  inviteMessageTone: 'soft' as const,
  partnerNickname: undefined as string | undefined,
  soloMode: null as boolean | null,
  mediatorStyleX: 50,
  mediatorStyleY: 50,
};

export const useSessionStore = create<SessionState>()(
  persist(
    (set) => ({
      ...initial,
      setRelationType: (t) => set({ relationType: t }),
      setCategory: (c) => set({ category: c }),
      setDescription: (text) => set({ description: text }),
      setInviteTone: (tone) => set({ inviteMessageTone: tone }),
      setSession: ({ id, inviteToken }) =>
        set({ sessionId: id, inviteToken }),
      setStatus: (s) => set({ status: s }),
      appendTurn: (t) =>
        set((prev) => ({ turns: [...prev.turns, t] })),
      setCurrentTurn: (n) => set({ currentTurn: n }),
      setRole: (r) => set({ role: r }),
      setPartnerNickname: (n) => set({ partnerNickname: n }),
      setSoloMode: (mode) => set({ soloMode: mode }),
      setMediatorStyle: (x, y) => set({ mediatorStyleX: x, mediatorStyleY: y }),
      reset: () => set(initial),
    }),
    {
      name: 'again-spring-session',
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
