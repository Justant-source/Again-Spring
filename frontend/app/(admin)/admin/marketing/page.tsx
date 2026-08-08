'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { Card } from '@/components/ui/card';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { PlatformCredentialsSection } from '@/components/admin/marketing/PlatformCredentialsSection';
import { PlatformAutoSection } from '@/components/admin/marketing/PlatformAutoSection';
import { JobBoard } from '@/components/admin/marketing/JobBoard';
import { PlatformPerformanceCards } from '@/components/admin/marketing/PlatformPerformanceCards';
import { PublicationTimeline } from '@/components/admin/marketing/PublicationTimeline';
import { HoldingControlsBar } from '@/components/admin/marketing/HoldingControlsBar';
import { HoldingBoard } from '@/components/admin/marketing/HoldingBoard';
import { HoldingDraftDialog } from '@/components/admin/marketing/HoldingDraftDialog';
import { RefreshControl } from '@/components/admin/RefreshControl';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { AdminTable } from '@/components/admin/AdminTable';
import {
  listMarketingJobs,
  publishMarketingJob,
  republishMarketingJob,
  getMarketingPerformance,
  getPublicationTimeline,
  getMarketingHoldingBoard,
  updateMarketingHoldingDraft,
  pinMarketingHolding,
  unpinMarketingHolding,
  listMarketingCompleted,
  forceMarketingCompleted,
  MarketingJob,
  MarketingHoldingBoard,
  MarketingHoldingRow,
  MarketingCompletedItem,
  MarketingForceMode,
  PlatformStatsDto,
  TimelineEventDto,
} from '@/lib/api/admin/marketing';

type MainTab = 'holding' | 'completed' | 'settings';

function resolveTab(raw: string | null): MainTab {
  if (raw === 'completed' || raw === 'jobs') return 'completed';
  if (raw === 'settings' || raw === 'credentials' || raw === 'quota') return 'settings';
  return 'holding';
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

  // Completed tab (holdings + jobs + analytics)
  const [completedItems, setCompletedItems] = useState<MarketingCompletedItem[]>(
    []
  );
  const [completedLoading, setCompletedLoading] = useState(false);
  const [completedError, setCompletedError] = useState<string | null>(null);
  const [forceBusyId, setForceBusyId] = useState<string | null>(null);
  const [jobs, setJobs] = useState<MarketingJob[]>([]);
  const [jobsLoading, setJobsLoading] = useState(true);
  const [jobsError, setJobsError] = useState<string | null>(null);
  const [performance, setPerformance] = useState<PlatformStatsDto[]>([]);
  const [timeline, setTimeline] = useState<TimelineEventDto[]>([]);
  const [perfLoading, setPerfLoading] = useState(false);

  const ACTIVE_STATUSES = ['QUEUED', 'RUNNING', 'READY', 'PUBLISHING'];

  const loadHolding = useCallback(async (showLoader = true) => {
    if (showLoader) setHoldingLoading(true);
    setHoldingError(null);
    try {
      const data = await getMarketingHoldingBoard();
      setBoard(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setHoldingError(`대기 보드를 불러오지 못했습니다: ${msg}`);
    } finally {
      if (showLoader) setHoldingLoading(false);
    }
  }, []);

  const loadJobs = useCallback(async (showLoader = true) => {
    if (showLoader) setJobsLoading(true);
    setJobsError(null);
    try {
      const data = await listMarketingJobs();
      setJobs(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setJobsError(`마케팅 잡 목록을 불러오지 못했습니다: ${msg}`);
    } finally {
      if (showLoader) setJobsLoading(false);
    }
  }, []);

  const loadAnalytics = useCallback(async () => {
    setPerfLoading(true);
    try {
      const [perf, tl] = await Promise.all([
        getMarketingPerformance(30),
        getPublicationTimeline(20),
      ]);
      setPerformance(perf);
      setTimeline(tl);
    } catch {
      // silently fail
    } finally {
      setPerfLoading(false);
    }
  }, []);

  const loadCompleted = useCallback(async () => {
    setCompletedLoading(true);
    setCompletedError(null);
    try {
      const items = await listMarketingCompleted({ limit: 50 });
      setCompletedItems(items);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setCompletedError(`완료/탈락 목록을 불러오지 못했습니다: ${msg}`);
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
      loadJobs();
      loadAnalytics();
    }
  }, [activeTab, loadCompleted, loadJobs, loadAnalytics]);

  useEffect(() => {
    if (activeTab !== 'completed') return;
    const hasActiveJobs = jobs.some((j) => ACTIVE_STATUSES.includes(j.status));
    if (!hasActiveJobs) return;
    const intervalId = setInterval(() => {
      loadJobs(false);
    }, 5000);
    return () => clearInterval(intervalId);
  }, [activeTab, jobs, loadJobs]);

  useEffect(() => {
    if (activeTab !== 'holding') return;
    const intervalId = setInterval(() => {
      loadHolding(false);
    }, 45000);
    return () => clearInterval(intervalId);
  }, [activeTab, loadHolding]);

  const handlePublish = async (id: number) => {
    try {
      const updated = await publishMarketingJob(id);
      setJobs((prev) => prev.map((j) => (j.id === id ? updated : j)));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      alert(`게시 요청에 실패했습니다: ${msg}`);
    }
  };

  const handleRepublish = async (id: number) => {
    try {
      const updated = await republishMarketingJob(id);
      setJobs((prev) => prev.map((j) => (j.id === id ? updated : j)));
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      alert(`게시 재시도에 실패했습니다: ${msg}`);
    }
  };

  const handlePin = async (row: MarketingHoldingRow) => {
    const raw = window.prompt(
      '핀 포맷을 입력하세요 (VIDEO 또는 TEXT).\n빈 값/취소 = 중단',
      row.projectedFormat === 'VIDEO' ? 'VIDEO' : 'TEXT'
    );
    if (raw == null) return;
    const format = raw.trim().toUpperCase();
    if (format !== 'VIDEO' && format !== 'TEXT') {
      alert('VIDEO 또는 TEXT만 가능합니다.');
      return;
    }
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

  const handleForce = async (item: MarketingCompletedItem) => {
    if (item.status !== 'DROPPED') {
      alert('강제 배포는 탈락(DROPPED) 건만 가능합니다.');
      return;
    }
    const raw = window.prompt(
      '강제 배포 모드 (일일 상한 무시)\nVIDEO_AND_TEXT 또는 TEXT_ONLY',
      'TEXT_ONLY'
    );
    if (raw == null) return;
    const mode = raw.trim().toUpperCase() as MarketingForceMode;
    if (mode !== 'VIDEO_AND_TEXT' && mode !== 'TEXT_ONLY') {
      alert('VIDEO_AND_TEXT 또는 TEXT_ONLY만 가능합니다.');
      return;
    }
    if (
      !window.confirm(
        `사연 ${item.postId}를 ${mode}로 강제 배포할까요?\n일일 상한을 무시합니다.`
      )
    ) {
      return;
    }
    setForceBusyId(item.postId);
    try {
      await forceMarketingCompleted(item.postId, mode);
      await Promise.all([loadCompleted(), loadJobs(false)]);
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
              onRefresh={() => {
                loadCompleted();
                loadJobs(true);
                loadAnalytics();
              }}
              loading={jobsLoading || completedLoading}
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
          <TabsTrigger value="settings">설정</TabsTrigger>
        </TabsList>

        <TabsContent value="holding" className="space-y-4">
          <p className="text-sm text-gray-500">
            24h 대기 N-top 보드입니다. 상한·가중치 저장 시 순위가 바로 다시 계산됩니다.
            핀은 상한 안에서 최우선이며, T+24h에 자동 확정됩니다.
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
              확정·탈락 홀딩과 잡·게시 상태입니다. 탈락 건은 상한을 무시하고 강제
              배포할 수 있습니다. 실패 잡 재시도는 일일 풀을 다시 쓰지 않습니다.
            </p>
          </div>

          {completedError && (
            <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {completedError}
            </div>
          )}
          {jobsError && (
            <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {jobsError}
            </div>
          )}

          <div className="mb-8" data-testid="marketing-completed-holdings">
            <h3 className="font-semibold text-gray-800 mb-4">홀딩 확정·탈락</h3>
            <div className="bg-white rounded-lg border">
              <AdminTable<MarketingCompletedItem>
                data={completedItems}
                loading={completedLoading}
                emptyMessage="확정/탈락 홀딩이 없습니다."
                rowKey={(row) => row.postId}
                columns={[
                  {
                    key: 'postId',
                    header: '사연',
                    render: (row) => (
                      <span className="font-mono text-xs">{row.postId}</span>
                    ),
                  },
                  {
                    key: 'status',
                    header: '상태',
                    render: (row) => (
                      <Badge
                        className={
                          row.status === 'DROPPED'
                            ? 'bg-red-100 text-red-800'
                            : 'bg-green-100 text-green-800'
                        }
                      >
                        {row.status}
                      </Badge>
                    ),
                  },
                  {
                    key: 'score',
                    header: '점수',
                    render: (row) => (
                      <span className="font-mono text-sm">
                        {row.scoreSnapshot != null
                          ? Number(row.scoreSnapshot).toFixed(1)
                          : '—'}
                      </span>
                    ),
                  },
                  {
                    key: 'jobs',
                    header: '잡',
                    render: (row) =>
                      row.jobs?.length
                        ? row.jobs
                            .map((j) => `#${j.id}:${j.status}`)
                            .join(', ')
                        : '—',
                  },
                  {
                    key: 'actions',
                    header: '액션',
                    render: (row) =>
                      row.status === 'DROPPED' ? (
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={forceBusyId === row.postId}
                          onClick={() => handleForce(row)}
                          data-testid={`completed-force-${row.postId}`}
                        >
                          강제 배포
                        </Button>
                      ) : (
                        <span className="text-xs text-gray-400">—</span>
                      ),
                  },
                ]}
              />
            </div>
          </div>

          {jobsLoading ? (
            <Card className="p-6">
              <div className="py-8 text-center text-gray-400">로드 중…</div>
            </Card>
          ) : (
            <>
              <div className="mb-8">
                <h3 className="font-semibold text-gray-800 mb-4">플랫폼 성과</h3>
                <PlatformPerformanceCards
                  data={performance}
                  loading={perfLoading}
                />
              </div>

              <div className="mb-8">
                <h3 className="font-semibold text-gray-800 mb-4">게시 이력</h3>
                <PublicationTimeline
                  events={timeline}
                  loading={perfLoading}
                />
              </div>

              <JobBoard
                jobs={jobs}
                onPublish={handlePublish}
                onRepublish={handleRepublish}
              />
            </>
          )}
        </TabsContent>

        <TabsContent value="settings" className="space-y-8">
          <PlatformAutoSection />
          <div className="border-t pt-6">
            <PlatformCredentialsSection />
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
