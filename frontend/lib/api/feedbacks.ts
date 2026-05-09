import { api } from './client';

export interface SubmitFeedbackPayload {
  category: string;
  content: string;
  contactConsent: boolean;
  contactEmail?: string;
  sessionId?: string | null;
  pageUrl?: string;
  userAgent?: string;
}

export async function submitFeedback(payload: SubmitFeedbackPayload): Promise<{ id: number }> {
  const res = await api.post('/api/feedbacks', {
    ...payload,
    pageUrl: payload.pageUrl ?? (typeof window !== 'undefined' ? window.location.href : undefined),
    userAgent: payload.userAgent ?? (typeof window !== 'undefined' ? navigator.userAgent : undefined),
  });
  return res.data;
}
