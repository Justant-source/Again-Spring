'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
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
import {
  listMarketingJobs,
  createMarketingJob,
  MarketingJob,
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
  { value: 'naver_blog', label: '네이버 블로그' },
  { value: 'x', label: 'X (트위터)' },
  { value: 'instagram_feed', label: '인스타그램 피드' },
  { value: 'instagram_reels', label: '인스타그램 릴스' },
  { value: 'youtube_shorts', label: 'YouTube Shorts' },
  { value: 'naver_clip', label: '네이버 클립' },
  { value: 'threads', label: 'Threads' },
];

export default function MarketingJobsPage() {
  const [activeTab, setActiveTab] = useState('jobs');
  const [jobs, setJobs] = useState<MarketingJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Create dialog state
  const [dialogOpen, setDialogOpen] = useState(false);
  const [postId, setPostId] = useState('');
  const [targets, setTargets] = useState<string[]>(['naver_blog']);
  const [autoPublish, setAutoPublish] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const ACTIVE_STATUSES = ['QUEUED', 'RUNNING', 'READY', 'PUBLISHING'];

  useEffect(() => {
    loadJobs();
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

  const toggleTarget = (value: string) => {
    setTargets((prev) =>
      prev.includes(value) ? prev.filter((t) => t !== value) : [...prev, value]
    );
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
      setTargets(['naver_blog']);
      setAutoPublish(false);
      await loadJobs();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setCreateError(`잡 생성에 실패했습니다: ${msg}`);
    } finally {
      setCreating(false);
    }
  };

  return (
    <AdminSection title="마케팅">
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="mb-4">
          <TabsTrigger value="jobs">마케팅 잡</TabsTrigger>
          <TabsTrigger value="credentials">플랫폼 계정</TabsTrigger>
        </TabsList>

        <TabsContent value="jobs">
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-gray-500">
          사연을 선택하고 ASM(Again-Spring-Marketing) 서버에 콘텐츠 생성을 요청합니다.
        </p>
        <div className="flex gap-2 items-center">
          <Button variant="outline" size="sm" onClick={() => loadJobs(true)} disabled={loading}>
            {loading ? '로드 중…' : '새로고침'}
          </Button>
          {jobs.some(j => ACTIVE_STATUSES.includes(j.status)) && (
            <span className="text-xs text-blue-500 animate-pulse">● 자동 갱신 중</span>
          )}
          <Button size="sm" onClick={() => { setCreateError(null); setDialogOpen(true); }}>
            + 새 마케팅 잡
          </Button>
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <Card className="p-6">
        {loading ? (
          <div className="py-8 text-center text-gray-400">로드 중…</div>
        ) : jobs.length === 0 ? (
          <div className="py-8 text-center text-gray-400">
            마케팅 잡이 없습니다.
            <br />
            <button
              className="mt-2 text-sm text-blue-500 hover:underline"
              onClick={() => setDialogOpen(true)}
            >
              + 첫 번째 잡 만들기
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>사연 ID</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>단계</TableHead>
                  <TableHead>진행률</TableHead>
                  <TableHead>타겟</TableHead>
                  <TableHead>생성일</TableHead>
                  <TableHead>액션</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {jobs.map((job) => (
                  <TableRow key={job.id}>
                    <TableCell className="font-mono text-sm">{job.id}</TableCell>
                    <TableCell className="max-w-[120px] truncate font-mono text-xs">
                      {job.postId}
                    </TableCell>
                    <TableCell>
                      <Badge className={STATUS_COLORS[job.status] || 'bg-gray-200'}>
                        {job.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm">{job.phase || '-'}</TableCell>
                    <TableCell className="text-sm">
                      {typeof job.progress === 'number'
                        ? `${Math.round(job.progress * 100)}%`
                        : '-'}
                    </TableCell>
                    <TableCell className="text-xs">
                      {(job.targets ?? []).join(', ')}
                    </TableCell>
                    <TableCell className="text-sm">
                      {new Date(job.createdAt).toLocaleDateString('ko-KR', {
                        month: 'short',
                        day: 'numeric',
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </TableCell>
                    <TableCell>
                      <Link href={`/admin/marketing/jobs/${job.id}`}>
                        <Button variant="outline" size="sm">상세</Button>
                      </Link>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>
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

          <div className="space-y-4 py-2">
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
    </AdminSection>
  );
}
