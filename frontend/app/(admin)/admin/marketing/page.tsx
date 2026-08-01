'use client';

import { useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { AdminPageHeader } from '@/components/admin/AdminPageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { PlatformCredentialsSection } from '@/components/admin/marketing/PlatformCredentialsSection';
import { JobBoard } from '@/components/admin/marketing/JobBoard';
import { PlatformPerformanceCards } from '@/components/admin/marketing/PlatformPerformanceCards';
import { PublicationTimeline } from '@/components/admin/marketing/PublicationTimeline';
import { PostPickerDialog } from '@/components/admin/marketing/PostPickerDialog';
import { RefreshControl } from '@/components/admin/RefreshControl';
import {
  listMarketingJobs,
  createMarketingJob,
  publishMarketingJob,
  republishMarketingJob,
  getMarketingPerformance,
  getPublicationTimeline,
  MarketingJob,
  PlatformStatsDto,
  TimelineEventDto,
} from '@/lib/api/admin/marketing';

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: 'bg-gray-200 text-gray-800',
  QUEUED: 'bg-blue-200 text-blue-800',
  RUNNING: 'bg-yellow-200 text-yellow-800',
  READY: 'bg-green-200 text-green-800',
  PUBLISHING: 'bg-orange-200 text-orange-800',
  PUBLISHED: 'bg-green-600 text-white',
  FAILED: 'bg-red-200 text-red-800',
  STALE: 'bg-gray-400 text-white',
  PARTIAL: 'bg-yellow-500 text-white',
};

const TARGET_PLATFORMS = [
  { value: 'x_thread', label: 'X 스레드' },
  { value: 'x', label: 'X (트위터)' },
  { value: 'naver_blog', label: '네이버 블로그' },
  { value: 'instagram_feed', label: '인스타그램 피드' },
  { value: 'instagram_reels', label: '인스타그램 릴스' },
  { value: 'youtube_shorts', label: 'YouTube Shorts' },
  { value: 'naver_clip', label: '네이버 클립' },
  { value: 'threads', label: 'Threads' },
];

export default function MarketingJobsPage() {
  const searchParams = useSearchParams();
  const [activeTab, setActiveTab] = useState(() => searchParams.get('tab') ?? 'jobs');
  const [jobs, setJobs] = useState<MarketingJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Create dialog state
  const [dialogOpen, setDialogOpen] = useState(false);
  const [pickerDialogOpen, setPickerDialogOpen] = useState(false);
  const [postId, setPostId] = useState('');
  const [targets, setTargets] = useState<string[]>(['x_thread']);
  const [autoPublish, setAutoPublish] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Analytics state
  const [performance, setPerformance] = useState<PlatformStatsDto[]>([]);
  const [timeline, setTimeline] = useState<TimelineEventDto[]>([]);
  const [perfLoading, setPerfLoading] = useState(false);

  const ACTIVE_STATUSES = ['QUEUED', 'RUNNING', 'READY', 'PUBLISHING'];

  useEffect(() => {
    loadJobs();
    loadAnalytics();
  }, []);

  useEffect(() => {
    const hasActiveJobs = jobs.some(j => ACTIVE_STATUSES.includes(j.status));
    if (!hasActiveJobs) return;
    const intervalId = setInterval(() => {
      loadJobs(false);
    }, 5000);
    return () => clearInterval(intervalId);
  }, [jobs]);

  const loadJobs = async (showLoader = true) => {
    if (showLoader) setLoading(true);
    setError(null);
    try {
      const data = await listMarketingJobs();
      setJobs(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setError(`마케팅 잡 목록을 불러오지 못했습니다: ${msg}`);
    } finally {
      if (showLoader) setLoading(false);
    }
  };

  const loadAnalytics = async () => {
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
  };

  const toggleTarget = (value: string) => {
    setTargets((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
  };

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

  const handleCreate = async () => {
    if (!postId.trim()) {
      setCreateError('사연 ID를 입력해주세요.');
      return;
    }
    if (targets.length === 0) {
      setCreateError('타겟 플랫폼을 최소 1개 선택해주세요.');
      return;
    }
    setCreating(true);
    setCreateError(null);
    try {
      await createMarketingJob(postId.trim(), targets, autoPublish);
      setDialogOpen(false);
      setPostId('');
      setTargets(['x_thread']);
      setAutoPublish(false);
      setPickerDialogOpen(false);
      await loadJobs();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setCreateError(`잡 생성에 실패했습니다: ${msg}`);
    } finally {
      setCreating(false);
    }
  };

  const handlePickPost = (pickedPostId: string) => {
    setPostId(pickedPostId);
    setPickerDialogOpen(false);
  };

  return (
    <div className="space-y-4">
      <AdminPageHeader
        title="마케팅"
        action={
          activeTab === 'jobs' && (
            <div className="flex items-center gap-2">
              <RefreshControl
                onRefresh={() => {
                  loadJobs(true);
                  loadAnalytics();
                }}
                loading={loading}
                autoRefreshSeconds={0}
              />
              <Button size="sm" onClick={() => { setCreateError(null); setDialogOpen(true); }}>
                + 새 마케팅 잡
              </Button>
            </div>
          )
        }
      />
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="mb-4">
          <TabsTrigger value="jobs">마케팅 잡</TabsTrigger>
          <TabsTrigger value="credentials">플랫폼 계정</TabsTrigger>
        </TabsList>

        <TabsContent value="jobs">
          <div className="mb-4">
            <p className="text-sm text-gray-500">
              사연을 선택하고 ASM(Again-Spring-Marketing) 서버에 콘텐츠 생성을 요청합니다.
            </p>
          </div>

          {error && (
            <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}

          {loading ? (
            <Card className="p-6">
              <div className="py-8 text-center text-gray-400">로드 중…</div>
            </Card>
          ) : (
            <>
              <JobBoard
                jobs={jobs}
                onPublish={handlePublish}
                onRepublish={handleRepublish}
              />

              <div className="mt-8 pt-6 border-t">
                <h3 className="font-semibold text-gray-800 mb-4">플랫폼 성과</h3>
                <PlatformPerformanceCards
                  data={performance}
                  loading={perfLoading}
                />
              </div>

              <div className="mt-8 pt-6 border-t">
                <h3 className="font-semibold text-gray-800 mb-4">게시 이력</h3>
                <PublicationTimeline
                  events={timeline}
                  loading={perfLoading}
                />
              </div>
            </>
          )}
        </TabsContent>

        <TabsContent value="credentials">
          <PlatformCredentialsSection />
        </TabsContent>
      </Tabs>

      {/* 새 마케팅 잡 생성 다이얼로그 */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>새 마케팅 잡 만들기</DialogTitle>
          </DialogHeader>

          <Tabs defaultValue="picker" className="w-full">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="picker">사연 선택</TabsTrigger>
              <TabsTrigger value="direct">직접 입력</TabsTrigger>
            </TabsList>

            <TabsContent value="picker" className="space-y-4 py-2">
              <div>
                <Label className="block mb-2 text-sm font-medium">
                  게시글 검색
                </Label>
                <Button
                  variant="outline"
                  className="w-full justify-start text-left"
                  onClick={() => setPickerDialogOpen(true)}
                >
                  {postId ? `선택됨: ${postId.substring(0, 20)}...` : '게시글 선택'}
                </Button>
              </div>
            </TabsContent>

            <TabsContent value="direct" className="space-y-4 py-2">
              <div className="space-y-1">
                <Label htmlFor="postId">사연 ID</Label>
                <Input
                  id="postId"
                  value={postId}
                  onChange={(e) => setPostId(e.target.value)}
                  placeholder="예: abc123def456"
                  className="font-mono text-sm"
                />
                <p className="text-xs text-gray-400">
                  사연 상세 페이지 URL의 마지막 경로 또는 DB의 post.id 값
                </p>
              </div>
            </TabsContent>
          </Tabs>

          <div className="space-y-4 py-2">

            <div className="space-y-2">
              <Label>타겟 플랫폼</Label>
              <div className="space-y-1">
                {TARGET_PLATFORMS.map((p) => (
                  <label key={p.value} className="flex cursor-pointer items-center gap-2">
                    <input
                      type="checkbox"
                      className="h-4 w-4"
                      checked={targets.includes(p.value)}
                      onChange={() => toggleTarget(p.value)}
                    />
                    <span className="text-sm">{p.label}</span>
                    <span className="text-xs text-gray-400">({p.value})</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <label className="flex cursor-pointer items-center gap-2">
                <input
                  type="checkbox"
                  className="h-4 w-4"
                  checked={autoPublish}
                  onChange={(e) => setAutoPublish(e.target.checked)}
                />
                <span className="text-sm font-medium">자동 게시</span>
                <span className="text-xs text-gray-400">
                  (콘텐츠 생성 완료 시 자동으로 각 플랫폼에 게시)
                </span>
              </label>
            </div>

            {createError && (
              <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {createError}
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)} disabled={creating}>
              취소
            </Button>
            <Button onClick={handleCreate} disabled={creating || targets.length === 0}>
              {creating ? '요청 중…' : 'ASM에 요청'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 사연 선택 다이얼로그 */}
      <PostPickerDialog
        open={pickerDialogOpen}
        onClose={() => setPickerDialogOpen(false)}
        onSelect={handlePickPost}
      />
    </div>
  );
}
