import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface VoteStore {
  /** postId → voted side ('g' | 'r') */
  votes: Record<string, 'g' | 'r'>;
  setVote: (postId: string, side: 'g' | 'r') => void;
  clearVote: (postId: string) => void;
  getVoteSide: (postId: string) => 'g' | 'r' | null;
}

export const useVoteStore = create<VoteStore>()(
  persist(
    (set, get) => ({
      votes: {},
      setVote: (postId, side) =>
        set((s) => ({ votes: { ...s.votes, [postId]: side } })),
      clearVote: (postId) =>
        set((s) => {
          const { [postId]: _, ...rest } = s.votes;
          return { votes: rest };
        }),
      getVoteSide: (postId) => get().votes[postId] ?? null,
    }),
    { name: 'again-spring-votes' }
  )
);
