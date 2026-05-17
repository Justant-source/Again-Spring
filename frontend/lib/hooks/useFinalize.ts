'use client';

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api/client';

export function useFinalize(
  sessionId: string,
  onRefresh: (opts?: { full?: boolean }) => Promise<void>,
) {
  const router = useRouter();
  const [finalizing, setFinalizing] = useState(false);
  const [finalizeError, setFinalizeError] = useState<string | null>(null);

  const handleFinalize = useCallback(async () => {
    if (finalizing) return;
    setFinalizeError(null);
    setFinalizing(true);
    try {
      const r = await api.post(`/api/sessions/${sessionId}/finalize`);
      if (r.data.completed) {
        router.push(`/session/result/${sessionId}`);
        return;
      } else if (r.data.awaitingPartner) {
        await onRefresh({ full: true });
      }
    } catch (e: any) {
      const msg =
        e.response?.data?.error?.message ||
        e.response?.data?.message ||
        '정리할 수 없어요. 잠시 후 다시 시도해 주세요.';
      setFinalizeError(msg);
    } finally {
      setFinalizing(false);
    }
  }, [sessionId, finalizing, onRefresh, router]);

  const handleAgreeFinalize = useCallback(async () => {
    try {
      const r = await api.post(`/api/sessions/${sessionId}/finalize/agree`);
      if (r.data.completed) {
        router.push(`/session/result/${sessionId}`);
      } else if (r.data.awaitingPartner) {
        await onRefresh({ full: true });
      }
    } catch (e) {
      console.error('Finalize agree failed:', e);
    }
  }, [sessionId, onRefresh, router]);

  const handleDeclineFinalize = useCallback(async () => {
    try {
      await api.post(`/api/sessions/${sessionId}/finalize/decline`);
      await onRefresh({ full: true });
    } catch (e) {
      console.error('Finalize decline failed:', e);
    }
  }, [sessionId, onRefresh]);

  const clearFinalizeError = useCallback(() => setFinalizeError(null), []);

  return { finalizing, finalizeError, clearFinalizeError, handleFinalize, handleAgreeFinalize, handleDeclineFinalize };
}
