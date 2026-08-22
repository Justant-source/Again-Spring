'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ArtifactSection } from '@/components/admin/marketing/ArtifactSection';
import { listAdminPostsForPicker, PickerPost } from '@/lib/api/admin/marketing';
import { useRenderTestStore, formatApiError, TestRun } from '@/lib/store/renderTestStore';
import { RefreshCw, Play, X } from 'lucide-react';

const STATUS_COLORS: Record<string, string> = {
  REQUESTED: 'bg-gray-200 text-gray-800',
  QUEUED: 'bg-blue-200 text-blue-800',
  RUNNING: 'bg-yellow-200 text-yellow-800',
  WAITING_EXTERNAL: 'bg-yellow-200 text-yellow-800',
  SLA_BREACHED: 'bg-orange-200 text-orange-800',
  READY: 'bg-green-200 text-green-800',
  FAILED: 'bg-red-200 text-red-800',
  STALE: 'bg-gray-400 text-white',
  PARTIAL: 'bg-yellow-500 text-white',
};

const TERMINAL_STATUSES = new Set(['READY', 'FAILED', 'STALE', 'PARTIAL']);

const TARGET_OPTIONS: Array<{ key: string; label: string }> = [
  { key: 'instagram_reels', label: '릴스 (세로 영상)' },
  { key: 'youtube_shorts', label: '쇼츠 (세로 영상)' },
];

function PostRow({
  post,
  onLaunch,
  launching,
}: {
  post: PickerPost;
  onLaunch: (post: PickerPost, targets: string[]) => void;
  launching: boolean;
}) {
  const [selected, setSelected] = useState<string[]>(['instagram_reels', 'youtube_shorts']);

  const toggle = (key: string) => {
    setSelected((prev) => (prev.includes(key) ? prev.filter((t) => t !== key) : [...prev, key]));
  };

  return (
    <div className="flex flex-col gap-2 rounded-lg border p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-gray-800">{post.title}</p>
        <p className="mt-0.5 text-xs text-gray-500">
          {post.category ?? '카테고리 없음'} · 댓글 {post.commentCount}
          {post.synthetic ? ' · AI 유저' : ''} · {new Date(post.createdAt).toLocaleString('ko-KR')}
        </p>
      </div>
      <div className="flex flex-shrink-0 items-center gap-3">
        <div className="flex gap-3 text-xs text-gray-600">
          {TARGET_OPTIONS.map((opt) => (
            <label key={opt.key} className="flex items-center gap-1">
              <input
                type="checkbox"
                checked={selected.includes(opt.key)}
                onChange={() => toggle(opt.key)}
              />
              {opt.label}
            </label>
          ))}
        </div>
        <Button
          size="sm"
          disabled={launching || selected.length === 0}
          onClick={() => onLaunch(post, selected)}
          data-testid="render-test-launch-button"
        >
          <Play className="mr-1 h-3 w-3" />
          테스트 렌더
        </Button>
      </div>
    </div>
  );
}

function TestRunCard({ run, onRemove }: { run: TestRun; onRemove: (runKey: string) => void }) {
  const job = run.job;
  const status = job?.status ?? (run.error ? 'ERROR' : 'REQUESTED');
  return (
    <Card className="p-4" data-testid="render-test-run-card">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-gray-800">{run.postTitle}</p>
          <p className="font-mono text-xs text-gray-500">
            {run.postId} · {run.targets.join(', ')}
            {job ? ` · Job ${job.id}` : ''}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge className={STATUS_COLORS[status] || 'bg-gray-200 text-gray-800'}>{status}</Badge>
          <button
            type="button"
            onClick={() => onRemove(run.runKey)}
            className="text-gray-400 hover:text-gray-600"
            aria-label="이 결과 지우기"
            data-testid="render-test-remove-run"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      </div>

      {run.error && (
        <p className="mt-2 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {run.error}
        </p>
      )}

      {job?.status === 'FAILED' && (job.errorSummary || job.errorMessage) && (
        <p className="mt-2 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {job.errorSummary || job.errorMessage}
        </p>
      )}

      {job && !TERMINAL_STATUSES.has(job.status) && (
        <p className="mt-2 text-xs text-gray-500">
          LLM 대본·시봄이 매핑 생성 후 WaggleBot 렌더 진행 중… (자동 갱신 5초 간격)
        </p>
      )}

      {job?.artifacts && Object.keys(job.artifacts).length > 0 && (
        <div className="mt-3">
          <ArtifactSection jobId={job.id} artifacts={job.artifacts} />
        </div>
      )}
    </Card>
  );
}

export function RenderTestSection() {
  const [posts, setPosts] = useState<PickerPost[]>([]);
  const [postsLoading, setPostsLoading] = useState(true);
  const [postsError, setPostsError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [manualPostId, setManualPostId] = useState('');
  const [launchingPostId, setLaunchingPostId] = useState<string | null>(null);

  const runs = useRenderTestStore((s) => s.runs);
  const launchRun = useRenderTestStore((s) => s.launch);
  const clearRuns = useRenderTestStore((s) => s.clearRuns);
  const removeRun = useRenderTestStore((s) => s.removeRun);

  const loadPosts = useCallback(async (targetPage: number) => {
    setPostsLoading(true);
    setPostsError(null);
    try {
      const data = await listAdminPostsForPicker(targetPage, 20);
      setPosts(data);
    } catch (err: unknown) {
      setPostsError(`최근 사연을 불러오지 못했습니다: ${formatApiError(err)}`);
    } finally {
      setPostsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPosts(page);
  }, [page, loadPosts]);

  const launch = useCallback(
    async (postId: string, postTitle: string, targets: string[]) => {
      setLaunchingPostId(postId);
      try {
        await launchRun(postId, postTitle, targets);
      } finally {
        setLaunchingPostId(null);
      }
    },
    [launchRun]
  );

  const handleManualLaunch = () => {
    const postId = manualPostId.trim();
    if (!postId) return;
    void launch(postId, postId, ['instagram_reels', 'youtube_shorts']);
  };

  const filteredPosts = search.trim()
    ? posts.filter((p) => p.title.toLowerCase().includes(search.trim().toLowerCase()))
    : posts;

  return (
    <div className="space-y-6">
      <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        <strong>테스트 전용 화면입니다.</strong> 여기서 만든 영상은 <code>autoPublish=false</code>로
        생성되어 실제 X·인스타그램·유튜브에는 절대 게시되지 않습니다. LLM으로 대본·시봄이 매핑을
        새로 생성하고 WaggleBot이 실제로 렌더링하므로, 완료까지 보통 1~3분 정도 걸립니다.
      </div>

      <Card className="p-4">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-gray-700">최근 사연에서 고르기</h3>
          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="제목 검색…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="rounded border px-2 py-1 text-sm"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => loadPosts(page)}
              disabled={postsLoading}
            >
              <RefreshCw className={`h-3 w-3 ${postsLoading ? 'animate-spin' : ''}`} />
            </Button>
          </div>
        </div>

        {postsError && (
          <div className="mb-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
            {postsError}
          </div>
        )}

        <div className="space-y-2">
          {filteredPosts.map((post) => (
            <PostRow
              key={post.id}
              post={post}
              onLaunch={(p, targets) => void launch(p.id, p.title, targets)}
              launching={launchingPostId === post.id}
            />
          ))}
          {!postsLoading && filteredPosts.length === 0 && (
            <p className="py-4 text-center text-sm text-gray-400">사연이 없습니다.</p>
          )}
        </div>

        <div className="mt-3 flex items-center justify-between">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0 || postsLoading}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            이전
          </Button>
          <span className="text-xs text-gray-400">페이지 {page + 1}</span>
          <Button
            variant="outline"
            size="sm"
            disabled={postsLoading || posts.length < 20}
            onClick={() => setPage((p) => p + 1)}
          >
            더 이전 사연
          </Button>
        </div>

        <div className="mt-4 border-t pt-3">
          <p className="mb-2 text-xs text-gray-500">
            목록에 없는 사연은 postId를 직접 입력해서 테스트할 수 있습니다 (릴스+쇼츠 동시 생성).
          </p>
          <div className="flex gap-2">
            <input
              type="text"
              placeholder="postId 직접 입력…"
              value={manualPostId}
              onChange={(e) => setManualPostId(e.target.value)}
              className="flex-1 rounded border px-2 py-1 text-sm font-mono"
            />
            <Button size="sm" variant="outline" onClick={handleManualLaunch} disabled={!manualPostId.trim()}>
              <Play className="mr-1 h-3 w-3" />
              테스트 렌더
            </Button>
          </div>
        </div>
      </Card>

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-sm font-semibold text-gray-700">
            이번 세션 테스트 결과 ({runs.length})
          </h3>
          {runs.length > 0 && (
            <Button variant="outline" size="sm" onClick={clearRuns} data-testid="render-test-clear-all">
              전체 지우기
            </Button>
          )}
        </div>
        <p className="mb-2 text-xs text-gray-400">
          다른 탭으로 이동해도 이 결과는 지워지지 않습니다. 직접 지우거나(카드의 ✕ 또는
          「전체 지우기」) 페이지를 새로고침하기 전까지 계속 남아 있습니다.
        </p>
        {runs.length === 0 ? (
          <p className="text-sm text-gray-400">
            위에서 사연을 골라 「테스트 렌더」를 누르면 여기 결과가 쌓입니다. 같은 사연을 여러 번
            눌러보면 LLM이 매번 다른 대본/시봄이 조합을 만드는 것을 비교해볼 수 있습니다.
          </p>
        ) : (
          <div className="space-y-3">
            {runs.map((run) => (
              <TestRunCard key={run.runKey} run={run} onRemove={removeRun} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
