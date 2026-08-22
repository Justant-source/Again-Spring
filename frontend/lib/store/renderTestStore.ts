'use client';

import { create } from 'zustand';
import { createMarketingTestJob, getMarketingJob, MarketingJob } from '@/lib/api/admin/marketing';

/**
 * 마케팅 「테스트」 탭의 렌더 실행 상태 — RenderTestSection 컴포넌트 밖(모듈 전역)에 둔다.
 * Radix TabsContent는 기본적으로 비활성 탭을 언마운트하므로(forceMount 없음), 컴포넌트
 * 로컬 useState에 두면 다른 탭을 봤다가 돌아올 때마다 실행 결과가 사라진다. 폴링 타이머도
 * 여기서 관리해 탭이 언마운트돼 있는 동안에도 상태 갱신이 끊기지 않는다.
 */

const TERMINAL_STATUSES = new Set(['READY', 'FAILED', 'STALE', 'PARTIAL']);

export function formatApiError(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as {
      response?: { status?: number; data?: { error?: { message?: string }; message?: string } };
      message?: string;
    };
    const serverMsg = anyErr.response?.data?.error?.message || anyErr.response?.data?.message;
    if (serverMsg) return serverMsg;
    if (anyErr.response?.status) return `HTTP ${anyErr.response.status}`;
    if (anyErr.message) return anyErr.message;
  }
  return String(err);
}

export interface TestRun {
  runKey: string;
  postId: string;
  postTitle: string;
  targets: string[];
  job: MarketingJob | null;
  error: string | null;
}

interface RenderTestStoreState {
  runs: TestRun[];
  launch: (postId: string, postTitle: string, targets: string[]) => Promise<void>;
  clearRuns: () => void;
  removeRun: (runKey: string) => void;
}

function pollJob(
  runKey: string,
  jobId: number,
  set: (fn: (s: RenderTestStoreState) => Partial<RenderTestStoreState>) => void
) {
  const tick = async () => {
    try {
      const job = await getMarketingJob(jobId);
      set((s) => ({ runs: s.runs.map((r) => (r.runKey === runKey ? { ...r, job } : r)) }));
      if (!TERMINAL_STATUSES.has(job.status)) {
        setTimeout(tick, 5000);
      }
    } catch (err: unknown) {
      set((s) => ({
        runs: s.runs.map((r) => (r.runKey === runKey ? { ...r, error: formatApiError(err) } : r)),
      }));
    }
  };
  setTimeout(tick, 3000);
}

export const useRenderTestStore = create<RenderTestStoreState>((set) => ({
  runs: [],

  launch: async (postId, postTitle, targets) => {
    const runKey = `${postId}-${targets.join('+')}-${Date.now()}`;
    set((s) => ({
      runs: [{ runKey, postId, postTitle, targets, job: null, error: null }, ...s.runs],
    }));
    try {
      const job = await createMarketingTestJob(postId, targets);
      set((s) => ({ runs: s.runs.map((r) => (r.runKey === runKey ? { ...r, job } : r)) }));
      if (!TERMINAL_STATUSES.has(job.status)) {
        pollJob(runKey, job.id, set);
      }
    } catch (err: unknown) {
      set((s) => ({
        runs: s.runs.map((r) => (r.runKey === runKey ? { ...r, error: formatApiError(err) } : r)),
      }));
    }
  },

  clearRuns: () => set({ runs: [] }),
  removeRun: (runKey) => set((s) => ({ runs: s.runs.filter((r) => r.runKey !== runKey) })),
}));
