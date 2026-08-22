'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ArtifactSection } from '@/components/admin/marketing/ArtifactSection';
import { listAdminPostsForPicker, listMarketingJobs, MarketingJob, PickerPost, resolveRenderProfile } from '@/lib/api/admin/marketing';
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
  onLaunch: (post: PickerPost, targets: string[], renderProfile?: string) => void;
  launching: boolean;
}) {
  const [selected, setSelected] = useState<string[]>(['instagram_reels', 'youtube_shorts']);
  const [renderProfile, setRenderProfile] = useState<string>('marketing_v2');

  const toggle = (key: string) => {
    setSelected((prev) => (prev.includes(key) ? prev.filter((t) => t !== key) : [...prev, key]));
  };

  return (
    <div className="flex flex-col gap-3 rounded-lg border p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
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
            onClick={() => onLaunch(post, selected, renderProfile)}
            data-testid="render-test-launch-button"
          >
            <Play className="mr-1 h-3 w-3" />
            테스트 렌더
          </Button>
        </div>
      </div>
      {/* 렌더 프로필 선택 */}
      <div className="flex items-center gap-3 border-t pt-2 text-xs">
        <span className="text-gray-600">렌더 프로필:</span>
        <label className="flex items-center gap-1">
          <input
            type="radio"
            name={`profile-${post.id}`}
            value="marketing_v2"
            checked={renderProfile === 'marketing_v2'}
            onChange={(e) => setRenderProfile(e.target.value)}
          />
          <span className="text-gray-700">v2 (신규)</span>
        </label>
        <label className="flex items-center gap-1">
          <input
            type="radio"
            name={`profile-${post.id}`}
            value="marketing_fast"
            checked={renderProfile === 'marketing_fast'}
            onChange={(e) => setRenderProfile(e.target.value)}
          />
          <span className="text-gray-700">fast (기존)</span>
        </label>
      </div>
    </div>
  );
}

function TestRunCard({ run, onRemove }: { run: TestRun; onRemove: (runKey: string) => void }) {
  const job = run.job;
  const status = job?.status ?? (run.error ? 'ERROR' : 'REQUESTED');
  const profile = run.renderProfile ?? 'unknown';

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
          <Badge
            className={profile === 'marketing_v2' ? 'bg-indigo-200 text-indigo-800' : 'bg-gray-200 text-gray-800'}
            data-testid="render-test-profile-badge"
          >
            {profile === 'marketing_v2' ? 'v2' : profile === 'marketing_fast' ? 'fast' : profile}
          </Badge>
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

function ServerJobCard({ job }: { job: MarketingJob }) {
  const profile = resolveRenderProfile(job);
  const diagnostics = job.generationDiagnostics as Record<string, unknown> | null | undefined;

  // 진단 요약 구성: 채널별 duration, comment_count, bgm, sfx_count, render_profile
  const buildDiagnosticSummary = (): string => {
    if (!diagnostics || typeof diagnostics !== 'object') return '';
    const parts: string[] = [];

    // 채널별 정보 수집 (instagram_reels, youtube_shorts 등)
    for (const key of ['instagram_reels', 'youtube_shorts', 'x_thread', 'instagram_feed']) {
      const channelData = (diagnostics[key] as Record<string, unknown>) || {};
      if (typeof channelData === 'object' && channelData !== null) {
        const duration = channelData.final_duration_ms;
        const comments = channelData.comment_count;
        if (duration != null) {
          const sec = Math.round((Number(duration) as number) / 1000);
          parts.push(`${key}: ${sec}s`);
        }
        if (comments != null) {
          parts.push(`댓글 ${comments}`);
        }
      }
    }

    // 글로벌 속성
    const bgm = diagnostics.bgm;
    const sfxCount = diagnostics.sfx_count;
    const renderProf = diagnostics.render_profile;
    if (bgm) parts.push(`🎵 ${String(bgm).split('/').pop()}`);
    if (sfxCount != null) parts.push(`SFX ×${sfxCount}`);
    if (renderProf) parts.push(`profile=${renderProf}`);

    return parts.join(' · ');
  };

  const diagSummary = buildDiagnosticSummary();

  return (
    <Card className="p-4" data-testid="render-test-server-job-card">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-mono text-sm font-semibold text-gray-800">{job.postId}</p>
          <p className="mt-0.5 text-xs text-gray-500">
            Job {job.id} · {job.targets.join(', ')} · {new Date(job.createdAt).toLocaleString('ko-KR')}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge
            className={profile === 'marketing_v2' ? 'bg-indigo-200 text-indigo-800' : 'bg-gray-200 text-gray-800'}
            data-testid="render-test-profile-badge"
          >
            {profile === 'marketing_v2' ? 'v2' : 'fast'}
          </Badge>
          <Badge className={STATUS_COLORS[job.status] || 'bg-gray-200 text-gray-800'}>{job.status}</Badge>
        </div>
      </div>

      {diagSummary && (
        <p className="mt-2 text-xs text-gray-600" data-testid="render-test-diag-summary">
          {diagSummary}
        </p>
      )}

      {job.status === 'FAILED' && (job.errorSummary || job.errorMessage) && (
        <p className="mt-2 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {job.errorSummary || job.errorMessage}
        </p>
      )}

      {!TERMINAL_STATUSES.has(job.status) && (
        <p className="mt-2 text-xs text-gray-500">
          LLM 대본·시봄이 매핑 생성 후 WaggleBot 렌더 진행 중… (렌더 파이프라인은 동시 1건
          처리라 대기열에 걸려 있을 수 있습니다)
        </p>
      )}

      {job.artifacts && Object.keys(job.artifacts).length > 0 && (
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
  const [profileFilter, setProfileFilter] = useState<'all' | 'v2' | 'fast'>('all');

  const runs = useRenderTestStore((s) => s.runs);
  const launchRun = useRenderTestStore((s) => s.launch);
  const clearRuns = useRenderTestStore((s) => s.clearRuns);
  const removeRun = useRenderTestStore((s) => s.removeRun);
  const resumePolling = useRenderTestStore((s) => s.resumePolling);

  // 새로고침 이후 복원된 run 중 아직 터미널 상태가 아닌 것들의 폴링을 다시 건다
  // (setTimeout 체인은 persist되지 않으므로 마운트 시 1회 재시작 필요).
  useEffect(() => {
    resumePolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 서버 전체의 최근 테스트 잡(autoPublish=false) — 브라우저·실행 주체와 무관하게 항상
  // 서버 기준 진실을 보여준다("이 브라우저에서 누른 것"만 보이는 로컬 세션 결과와는 별개).
  const [serverJobs, setServerJobs] = useState<MarketingJob[]>([]);
  const [serverJobsLoading, setServerJobsLoading] = useState(true);
  const [serverJobsError, setServerJobsError] = useState<string | null>(null);

  const loadServerJobs = useCallback(async (showLoader = true) => {
    if (showLoader) setServerJobsLoading(true);
    setServerJobsError(null);
    try {
      const all = await listMarketingJobs();
      const testJobs = all
        .filter((j) => !j.autoPublish)
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
        .slice(0, 20);
      setServerJobs(testJobs);
    } catch (err: unknown) {
      setServerJobsError(`서버 테스트 잡 목록을 불러오지 못했습니다: ${formatApiError(err)}`);
    } finally {
      if (showLoader) setServerJobsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadServerJobs();
  }, [loadServerJobs]);

  // 미종료 잡이 있으면 10초마다 자동 갱신 (누가/어디서 실행했든 진행 상황 반영).
  useEffect(() => {
    const hasActive = serverJobs.some((j) => !TERMINAL_STATUSES.has(j.status));
    if (!hasActive) return;
    const t = setInterval(() => loadServerJobs(false), 10000);
    return () => clearInterval(t);
  }, [serverJobs, loadServerJobs]);

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

  const [manualRenderProfile, setManualRenderProfile] = useState<string>('marketing_v2');

  const launch = useCallback(
    async (postId: string, postTitle: string, targets: string[], renderProfile?: string) => {
      setLaunchingPostId(postId);
      try {
        await launchRun(postId, postTitle, targets, renderProfile);
      } finally {
        setLaunchingPostId(null);
      }
    },
    [launchRun]
  );

  const handleManualLaunch = () => {
    const postId = manualPostId.trim();
    if (!postId) return;
    void launch(postId, postId, ['instagram_reels', 'youtube_shorts'], manualRenderProfile);
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
        <br />
        <strong>렌더 프로필(v2·fast)을 선택해서 같은 사연의 다른 버전을 비교해볼 수 있습니다.</strong> v2는 새로운 화질 개선 설정입니다.
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
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <input
              type="text"
              placeholder="postId 직접 입력…"
              value={manualPostId}
              onChange={(e) => setManualPostId(e.target.value)}
              className="flex-1 rounded border px-2 py-1 text-sm font-mono"
            />
            {/* 렌더 프로필 선택 (직접 입력) */}
            <div className="flex items-center gap-2 text-xs">
              <label className="flex items-center gap-1">
                <input
                  type="radio"
                  name="manual-profile"
                  value="marketing_v2"
                  checked={manualRenderProfile === 'marketing_v2'}
                  onChange={(e) => setManualRenderProfile(e.target.value)}
                />
                <span className="text-gray-700">v2</span>
              </label>
              <label className="flex items-center gap-1">
                <input
                  type="radio"
                  name="manual-profile"
                  value="marketing_fast"
                  checked={manualRenderProfile === 'marketing_fast'}
                  onChange={(e) => setManualRenderProfile(e.target.value)}
                />
                <span className="text-gray-700">fast</span>
              </label>
            </div>
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
            서버의 최근 테스트 잡 ({serverJobs.length})
          </h3>
          <Button
            variant="outline"
            size="sm"
            onClick={() => loadServerJobs(true)}
            disabled={serverJobsLoading}
            data-testid="render-test-server-refresh"
          >
            <RefreshCw className={`h-3 w-3 ${serverJobsLoading ? 'animate-spin' : ''}`} />
          </Button>
        </div>
        <p className="mb-2 text-xs text-gray-400">
          누가·어디서(이 화면·다른 브라우저·스크립트) 실행했든 서버에 있는 최근
          테스트 잡(autoPublish=false) 20건을 그대로 보여줍니다. 진행 중인 잡이 있으면
          10초마다 자동 갱신됩니다.
        </p>

        {/* 프로필 필터 */}
        {serverJobs.length > 0 && (
          <div className="mb-3 flex items-center gap-2 text-xs">
            <span className="text-gray-600">프로필 필터:</span>
            <label className="flex items-center gap-1">
              <input
                type="radio"
                name="profile-filter"
                value="all"
                checked={profileFilter === 'all'}
                onChange={(e) => setProfileFilter(e.target.value as typeof profileFilter)}
              />
              <span className="text-gray-700">전체</span>
            </label>
            <label className="flex items-center gap-1">
              <input
                type="radio"
                name="profile-filter"
                value="v2"
                checked={profileFilter === 'v2'}
                onChange={(e) => setProfileFilter(e.target.value as typeof profileFilter)}
              />
              <span className="text-gray-700">v2만</span>
            </label>
            <label className="flex items-center gap-1">
              <input
                type="radio"
                name="profile-filter"
                value="fast"
                checked={profileFilter === 'fast'}
                onChange={(e) => setProfileFilter(e.target.value as typeof profileFilter)}
              />
              <span className="text-gray-700">fast만</span>
            </label>
          </div>
        )}

        {serverJobsError && (
          <div className="mb-3 rounded border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
            {serverJobsError}
          </div>
        )}

        {serverJobs.length === 0 && !serverJobsLoading ? (
          <p className="text-sm text-gray-400">아직 서버에 테스트 잡이 없습니다.</p>
        ) : (
          <div className="space-y-4">
            {/* Side-by-side 비교 섹션 — 같은 postId에 v2와 fast가 둘 다 있는 경우 */}
            {(() => {
              const groupedByPost = new Map<string, MarketingJob[]>();
              for (const job of serverJobs) {
                const profile = resolveRenderProfile(job);
                if (profileFilter === 'v2' && profile !== 'marketing_v2') continue;
                if (profileFilter === 'fast' && profile !== 'marketing_fast') continue;
                if (!groupedByPost.has(job.postId)) {
                  groupedByPost.set(job.postId, []);
                }
                groupedByPost.get(job.postId)!.push(job);
              }

              const comparePairs = Array.from(groupedByPost.values()).filter((jobs) => jobs.length >= 2);

              return comparePairs.length > 0 ? (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                  <h4 className="mb-3 text-sm font-semibold text-amber-900">프로필 비교</h4>
                  <div className="space-y-4">
                    {comparePairs.map((jobs) => {
                      const v2Job = jobs.find((j) => resolveRenderProfile(j) === 'marketing_v2');
                      const fastJob = jobs.find((j) => resolveRenderProfile(j) === 'marketing_fast');
                      return (
                        <div key={jobs[0].postId} className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                          {v2Job && (
                            <div>
                              <div className="mb-2 flex items-center gap-2">
                                <Badge className="bg-indigo-200 text-indigo-800">v2</Badge>
                                <span className="text-xs text-gray-600">Job {v2Job.id}</span>
                              </div>
                              <ServerJobCard job={v2Job} />
                            </div>
                          )}
                          {fastJob && (
                            <div>
                              <div className="mb-2 flex items-center gap-2">
                                <Badge className="bg-gray-200 text-gray-800">fast</Badge>
                                <span className="text-xs text-gray-600">Job {fastJob.id}</span>
                              </div>
                              <ServerJobCard job={fastJob} />
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : null;
            })()}

            {/* 일반 잡 목록 */}
            <div>
              {(() => {
                const filtered = serverJobs.filter((job) => {
                  const profile = resolveRenderProfile(job);
                  if (profileFilter === 'v2') return profile === 'marketing_v2';
                  if (profileFilter === 'fast') return profile === 'marketing_fast';
                  return true;
                });
                return filtered.map((job) => (
                  <ServerJobCard key={job.id} job={job} />
                ));
              })()}
            </div>
          </div>
        )}
      </div>

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
          위 「서버의 최근 테스트 잡」과 달리 이 목록은 <strong>이 브라우저에서 직접 「테스트
          렌더」를 눌렀을 때만</strong> 쌓입니다(로컬 저장). 다른 탭 이동·새로고침에도 지워지지
          않고, 직접 지우기 전까지(카드의 ✕ 또는 「전체 지우기」) 남아 있습니다.
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
