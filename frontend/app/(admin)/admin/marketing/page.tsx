'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { PlatformCredentialsSection } from '@/components/admin/marketing/PlatformCredentialsSection';
import { ShortformVideoSection } from '@/components/admin/marketing/ShortformVideoSection';
import { SfxMappingSection } from '@/components/admin/marketing/SfxMappingSection';
import { PlatformAutoSection } from '@/components/admin/marketing/PlatformAutoSection';
import { HoldingControlsBar } from '@/components/admin/marketing/HoldingControlsBar';
import { HoldingBoard } from '@/components/admin/marketing/HoldingBoard';
import { HoldingDraftDialog } from '@/components/admin/marketing/HoldingDraftDialog';
import { MarketingStatsTab } from '@/components/admin/marketing/MarketingStatsTab';
import { RenderTestSection } from '@/components/admin/marketing/RenderTestSection';
import {
  CompletedHoldingsBoard,
  type CompletedHoldingView,
} from '@/components/admin/marketing/CompletedHoldingsBoard';
import { CompletedPublicationDialog } from '@/components/admin/marketing/CompletedPublicationDialog';
import { JobRedriveDialog } from '@/components/admin/marketing/JobRedriveDialog';
import { FailedJobsBoard } from '@/components/admin/marketing/FailedJobsBoard';
import { EditPublishedThreadDialog } from '@/components/admin/content/EditPublishedThreadDialog';
import { RefreshControl } from '@/components/admin/RefreshControl';
import {
  getMarketingHoldingBoard,
  updateMarketingHoldingDraft,
  pinMarketingHolding,
  unpinMarketingHolding,
  listMarketingCompleted,
  forceMarketingCompleted,
  MarketingHoldingBoard,
  MarketingHoldingRow,
  MarketingPinFormat,
  MarketingForceMode,
} from '@/lib/api/admin/marketing';

type MainTab = 'holding' | 'completed' | 'stats' | 'test' | 'settings';

function resolveTab(raw: string | null): MainTab {
  if (raw === 'completed' || raw === 'jobs') return 'completed';
  if (raw === 'stats' || raw === 'report') return 'stats';
  if (raw === 'test') return 'test';
  if (raw === 'settings' || raw === 'credentials' || raw === 'quota') return 'settings';
  return 'holding';
}

function formatApiError(err: unknown): string {
  if (typeof err === 'object' && err !== null) {
    const anyErr = err as {
      response?: { status?: number; data?: { error?: { message?: string }; message?: string; detail?: string } };
      message?: string;
      code?: string;
    };
    const data = anyErr.response?.data;
    const serverMsg =
      data?.error?.message || data?.message || data?.detail;
    if (serverMsg) return serverMsg;
    if (anyErr.response?.status) {
      return `HTTP ${anyErr.response.status}${anyErr.message ? ` (${anyErr.message})` : ''}`;
    }
    if (anyErr.message) return anyErr.message;
  }
  return String(err);
}

/** Extract all FAILED job IDs from completed items. */
function getFailedJobIds(items: CompletedHoldingView[]): number[] {
  const failedIds = new Set<number>();
  for (const item of items) {
    for (const job of item.jobs ?? []) {
      if (job.status === 'FAILED') {
        failedIds.add(job.id);
      }
    }
  }
  return Array.from(failedIds).sort((a, b) => a - b);
}

export default function MarketingJobsPage() {
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState<MainTab>(() =>
    resolveTab(searchParams.get('tab'))
  );

  // Holding tab
  const [board, setBoard] = useState<MarketingHoldingBoard | null>(null);
  const [holdingLoading, setHoldingLoading] = useState(true);
  const [holdingError, setHoldingError] = useState<string | null>(null);
  const [editRow, setEditRow] = useState<MarketingHoldingRow | null>(null);
  const [draftSaving, setDraftSaving] = useState(false);
  const [draftError, setDraftError] = useState<string | null>(null);

  // Completed tab (게시 이력 + 탈락 홀딩)
  const [completedItems, setCompletedItems] = useState<CompletedHoldingView[]>([]);
  const [completedLoading, setCompletedLoading] = useState(false);
  const [completedError, setCompletedError] = useState<string | null>(null);
  const [forceBusyId, setForceBusyId] = useState<string | null>(null);
  const [selectedCompletedItem, setSelectedCompletedItem] =
    useState<CompletedHoldingView | null>(null);
  const [viewPostId, setViewPostId] = useState<string | null>(null);

  // Redrive failed jobs
  const [redriveOpen, setRedriveOpen] = useState(false);
  const [redriveSuccess, setRedriveSuccess] = useState<string | null>(null);

  const loadHolding = useCallback(async (showLoader = true) => {
    if (showLoader) setHoldingLoading(true);
    setHoldingError(null);
    try {
      const data = await getMarketingHoldingBoard();
      setBoard(data);
    } catch (err: unknown) {
      setHoldingError(`대기 보드를 불러오지 못했습니다: ${formatApiError(err)}`);
    } finally {
      if (showLoader) setHoldingLoading(false);
    }
  }, []);

  const loadCompleted = useCallback(async () => {
    setCompletedLoading(true);
    setCompletedError(null);
    try {
      const items = await listMarketingCompleted({ limit: 50 });
      setCompletedItems(items);
    } catch (err: unknown) {
      setCompletedError(`확정/탈락 목록을 불러오지 못했습니다: ${formatApiError(err)}`);
    } finally {
      setCompletedLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHolding();
  }, [loadHolding]);

  useEffect(() => {
    if (activeTab === 'completed') {
      loadCompleted();
    }
  }, [activeTab, loadCompleted]);

  useEffect(() => {
    if (activeTab !== 'holding') return;
    const intervalId = setInterval(() => {
      loadHolding(false);
    }, 45000);
    return () => clearInterval(intervalId);
  }, [activeTab, loadHolding]);

  const handlePin = async (row: MarketingHoldingRow, format: MarketingPinFormat) => {
    try {
      await pinMarketingHolding(row.postId, format);
      await loadHolding(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      alert(`핀 실패: ${msg}`);
    }
  };

  const handleUnpin = async (row: MarketingHoldingRow) => {
    try {
      await unpinMarketingHolding(row.postId);
      await loadHolding(false);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      alert(`핀 해제 실패: ${msg}`);
    }
  };

  const handleForce = async (postId: string, mode: MarketingForceMode) => {
    // Confirm is the board's explicit 「강제 배포」 button (no window.prompt/confirm).
    setForceBusyId(postId);
    try {
      await forceMarketingCompleted(postId, mode);
      await loadCompleted();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      alert(`강제 배포 실패: ${msg}`);
    } finally {
      setForceBusyId(null);
    }
  };

  return (
    <div className="space-y-4">
      <AdminPageHeader
        title="마케팅"
        action={
          activeTab === 'holding' ? (
            <RefreshControl
              onRefresh={() => loadHolding(true)}
              loading={holdingLoading}
              autoRefreshSeconds={0}
            />
          ) : activeTab === 'completed' ? (
            <RefreshControl
              onRefresh={() => loadCompleted()}
              loading={completedLoading}
              autoRefreshSeconds={0}
            />
          ) : undefined
        }
      />

      <Tabs
        value={activeTab}
        onValueChange={(v) => setActiveTab(resolveTab(v))}
      >
        <TabsList className="mb-4">
          <TabsTrigger value="holding">대기</TabsTrigger>
          <TabsTrigger value="completed">완료</TabsTrigger>
          <TabsTrigger value="stats">통계</TabsTrigger>
          <TabsTrigger value="test">테스트</TabsTrigger>
          <TabsTrigger value="settings">설정</TabsTrigger>
        </TabsList>

        <TabsContent value="holding" className="space-y-4">
          <p className="text-sm text-gray-500">
            24h 대기 N-top 보드입니다. 상한·가중치 저장 시 순위가 바로 다시 계산됩니다.
            핀은 상한 안에서 최우선이며, T+24h에 자동 확정됩니다.
            24시간이 지났는데 아직 확정되지 않은 사연은 맨 위에 「24h 경과 · 확정 재시도」로 남습니다.
          </p>

          <HoldingControlsBar onSaved={() => loadHolding(false)} />

          {holdingError && (
            <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {holdingError}
            </div>
          )}

          {board && (
            <p className="text-xs text-gray-500">
              컷라인 N={board.meta.cutline} · 잔여 풀 {board.meta.remainingPool}
              {' · '}오늘 영상 {board.meta.videosToday ?? '—'} / 글{' '}
              {board.meta.textsToday ?? '—'}
            </p>
          )}

          <HoldingBoard
            rows={board?.items ?? []}
            loading={holdingLoading}
            cutline={board?.meta.cutline}
            onEdit={setEditRow}
            onOpenPost={(row) => setViewPostId(row.postId)}
            onPin={handlePin}
            onUnpin={handleUnpin}
          />

          <HoldingDraftDialog
            open={!!editRow}
            postId={editRow?.postId ?? null}
            draft={editRow?.draft}
            readOnly={!!editRow?.lockedAt}
            saving={draftSaving}
            error={draftError}
            onClose={() => {
              setEditRow(null);
              setDraftError(null);
            }}
            onSave={async ({ postId, draft }) => {
              setDraftSaving(true);
              setDraftError(null);
              try {
                await updateMarketingHoldingDraft(postId, draft);
                setEditRow(null);
                await loadHolding(false);
              } catch (err: unknown) {
                const msg = err instanceof Error ? err.message : String(err);
                setDraftError(msg);
              } finally {
                setDraftSaving(false);
              }
            }}
          />
        </TabsContent>

        <TabsContent value="completed">
          <div className="mb-4">
            <p className="text-sm text-gray-500">
              확정(게시 이력)·탈락 홀딩입니다. 게시 이력 행을 클릭하면 플랫폼별 게시
              상세를 볼 수 있습니다. 탈락·확정 건 모두 강제 배포로 빠진 채널(예: 영상)을
              추가할 수 있습니다. 기본 모드는 영상+글입니다.
            </p>
          </div>

          {completedError && (
            <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {completedError}
            </div>
          )}

          {redriveSuccess && (
            <div className="mb-4 rounded border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">
              {redriveSuccess}
              <button
                type="button"
                className="ml-2 text-green-600 hover:text-green-800 font-medium"
                onClick={() => setRedriveSuccess(null)}
              >
                닫기
              </button>
            </div>
          )}

          {getFailedJobIds(completedItems).length > 0 && (
            <div className="mb-4">
              <button
                type="button"
                onClick={() => setRedriveOpen(true)}
                className="inline-flex items-center px-3 py-2 rounded-md text-sm font-medium bg-orange-50 text-orange-700 border border-orange-200 hover:bg-orange-100 transition"
                data-testid="completed-redrive-button"
              >
                ⚠️ 실패 잡 일괄 재구동 ({getFailedJobIds(completedItems).length})
              </button>
            </div>
          )}

          <CompletedHoldingsBoard
            items={completedItems}
            loading={completedLoading}
            onRowClick={setSelectedCompletedItem}
            onOpenDroppedPost={(item) => setViewPostId(item.postId)}
            onForce={handleForce}
            forceBusyPostId={forceBusyId}
          />

          <FailedJobsBoard
            items={completedItems}
            loading={completedLoading}
            onRedriveSuccess={(response) => {
              setRedriveSuccess(
                `재구동 완료: ${response.requested}개 요청, ${response.results.length}개 결과`
              );
              // Refresh completed items to reflect updated status
              void loadCompleted();
            }}
            className="mt-8"
          />

          <CompletedPublicationDialog
            open={!!selectedCompletedItem}
            item={selectedCompletedItem}
            onClose={() => setSelectedCompletedItem(null)}
          />

          <JobRedriveDialog
            open={redriveOpen}
            failedJobIds={getFailedJobIds(completedItems)}
            loading={completedLoading}
            error={completedError}
            onClose={() => setRedriveOpen(false)}
            onSuccess={(response) => {
              setRedriveSuccess(
                `재구동 완료: ${response.requested}개 요청, ${response.results.length}개 결과`
              );
              // Refresh completed items to reflect updated status
              void loadCompleted();
            }}
          />
        </TabsContent>

        <TabsContent value="stats" className="space-y-4">
          <MarketingStatsTab />
        </TabsContent>

        <TabsContent value="test" className="space-y-4">
          <RenderTestSection />
        </TabsContent>

        <TabsContent value="settings" className="space-y-8">
          <PlatformAutoSection />
          <div className="border-t pt-6">
            <ShortformVideoSection />
          </div>
          <div className="border-t pt-6">
            <SfxMappingSection />
          </div>
          <div className="border-t pt-6">
            <PlatformCredentialsSection />
          </div>
        </TabsContent>
      </Tabs>

      <EditPublishedThreadDialog
        postId={viewPostId}
        onClose={() => setViewPostId(null)}
        onSaved={() => {
          setViewPostId(null);
          if (activeTab === 'holding') void loadHolding(false);
          if (activeTab === 'completed') void loadCompleted();
        }}
        onDeleted={() => {
          setViewPostId(null);
          if (activeTab === 'holding') void loadHolding(false);
          if (activeTab === 'completed') void loadCompleted();
        }}
      />
    </div>
  );
}
