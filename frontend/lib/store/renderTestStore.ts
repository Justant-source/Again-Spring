'use client';

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { createMarketingTestJob, getMarketingJob, MarketingJob } from '@/lib/api/admin/marketing';

/**
 * 마케팅 「테스트」 탭의 렌더 실행 상태 — RenderTestSection 컴포넌트 밖(모듈 전역)에 두고
 * localStorage에 영속화한다.
 *
 * 1) Radix TabsContent는 기본적으로 비활성 탭을 언마운트하므로(forceMount 없음), 컴포넌트
 *    로컬 useState에 두면 다른 탭을 봤다가 돌아올 때마다 실행 결과가 사라진다.
 * 2) 사용자 요구사항은 "직접 수동으로 삭제하지 않는 이상 계속 남아있어야" — 새로고침도
 *    수동 삭제가 아니므로 새로고침에도 살아남아야 한다. 그래서 zustand persist로
 *    localStorage에 저장한다(카카오톡 인앱 등 저장 제한 환경 대비 try-catch 래핑,
 *    userStore.ts와 동일 패턴).
 *
 * 새로고침 후에는 폴링 setTimeout 체인 자체는 살아남지 않으므로, RenderTestSection이
 * 마운트될 때 resumePolling()을 호출해 미종료 상태(job 있고 터미널 아님)인 run들의
 * 폴링을 다시 건다. job이 아직 null인 채로 새로고침된 run(요청이 전송 중이었던 경우)은
 * 원 요청을 복구할 방법이 없어 그대로 남는다 — 사용자가 지우거나 다시 실행해야 한다.
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
  renderProfile?: string; // 이 실행에서 사용한 렌더 프로필 ('marketing_v2'|'marketing_fast')
  job: MarketingJob | null;
  error: string | null;
}

interface RenderTestStoreState {
  runs: TestRun[];
  launch: (postId: string, postTitle: string, targets: string[], renderProfile?: string) => Promise<void>;
  clearRuns: () => void;
  removeRun: (runKey: string) => void;
  resumePolling: () => void;
}

// 카카오톡 인앱 등 localStorage 제한 환경에서 setItem/removeItem이 throw할 수 있으므로
// 모든 접근을 try-catch로 감싼다 (lib/store/userStore.ts와 동일 패턴).
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

export const useRenderTestStore = create<RenderTestStoreState>()(
  persist(
    (set, get) => ({
      runs: [],

      launch: async (postId, postTitle, targets, renderProfile) => {
        const runKey = `${postId}-${targets.join('+')}-${renderProfile ?? 'default'}-${Date.now()}`;
        set((s) => ({
          runs: [{ runKey, postId, postTitle, targets, renderProfile, job: null, error: null }, ...s.runs],
        }));
        try {
          const job = await createMarketingTestJob(postId, targets, renderProfile);
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

      resumePolling: () => {
        for (const r of get().runs) {
          if (r.job && !TERMINAL_STATUSES.has(r.job.status)) {
            pollJob(r.runKey, r.job.id, set);
          }
        }
      },
    }),
    {
      name: 'again-spring-render-test-runs',
      storage: createJSONStorage(() => safeStorage),
      partialize: (s) => ({ runs: s.runs }),
    }
  )
);
