'use client';

import { create } from 'zustand';

interface GuestLimitModalState {
  sessionId: string | null;
}

interface FeedbackModalState {
  sessionId?: string | null;
}

interface UiState {
  guestLimitModal: GuestLimitModalState | null;
  showGuestLimitModal: (sessionId: string | null) => void;
  hideGuestLimitModal: () => void;
  dailyLimitModal: boolean;
  showDailyLimitModal: () => void;
  hideDailyLimitModal: () => void;
  feedbackModal: FeedbackModalState | null;
  showFeedbackModal: (sessionId?: string | null) => void;
  hideFeedbackModal: () => void;
}

export const useUiStore = create<UiState>((set) => ({
  guestLimitModal: null,
  showGuestLimitModal: (sessionId) => set({ guestLimitModal: { sessionId } }),
  hideGuestLimitModal: () => set({ guestLimitModal: null }),
  dailyLimitModal: false,
  showDailyLimitModal: () => set({ dailyLimitModal: true }),
  hideDailyLimitModal: () => set({ dailyLimitModal: false }),
  feedbackModal: null,
  showFeedbackModal: (sessionId) => set({ feedbackModal: { sessionId } }),
  hideFeedbackModal: () => set({ feedbackModal: null }),
}));
