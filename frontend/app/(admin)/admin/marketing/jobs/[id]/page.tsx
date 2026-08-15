'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { AdminSection } from '@/components/admin/AdminSection';
import {
  getMarketingJob,
  publishMarketingJob,
  republishMarketingJob,
  regenerateMarketingJob,
  getJobTraffic,
  MarketingJob,
  JobTrafficDto,
} from '@/lib/api/admin/marketing';
import { ExternalLink, AlertTriangle, RefreshCw } from 'lucide-react';
import { ArtifactSection } from '@/components/admin/marketing/ArtifactSection';

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

const PUB_STATE_COLORS: Record<string, string> = {
  PUBLISHED: 'bg-green-200 text-green-800',
  NEEDS_AUTH: 'bg-red-200 text-red-800',
  FAILED: 'bg-red-200 text-red-800',
  MANUAL: 'bg-gray-200 text-gray-800',
  PENDING: 'bg-blue-100 text-blue-800',
  PUBLISHING: 'bg-orange-100 text-orange-800',
};

const ACTIVE_STATUSES = new Set(['REQUESTED', 'QUEUED', 'RUNNING', 'PUBLISHING']);
const TERMINAL_STATUSES = new Set(['PUBLISHED', 'PARTIAL', 'FAILED', 'STALE']);

function hasNeedsAuth(job: MarketingJob): boolean {
  return (job.publications ?? []).some((p) => p.state === 'NEEDS_AUTH');
}

export default function MarketingJobDetailPage() {
  const params = useParams();
  const router = useRouter();
  const [job, setJob] = useState<MarketingJob | null>(null);
  const [loading, setLoading] = useState(true);
  const [publishing, setPublishing] = useState(false);
  const [republishing, setRepublishing] = useState(false);
  const [regenerating, setRegenerating] = useState(false);
  const [traffic, setTraffic] = useState<JobTrafficDto | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPoll = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPoll = () => {
    stopPoll();
    pollRef.current = setInterval(async () => {
      try {
        const data = await getMarketingJob(parseInt(params.id as string));
        setJob(data);
        if (TERMINAL_STATUSES.has(data.status)) stopPoll();
      } catch {
        // keep polling — transient error
      }
    }, 3000);
  };

  useEffect(() => {
    loadJob();
    return stopPoll;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.id]);

  useEffect(() => {
    if (job?.status === 'PUBLISHED') {
      getJobTraffic(job.id)
        .then(setTraffic)
        .catch(() => {
          // silently fail
        });
    }
  }, [job?.status]);

  const loadJob = async () => {
    setLoading(true);
    try {
      const data = await getMarketingJob(parseInt(params.id as string));
      setJob(data);
      if (ACTIVE_STATUSES.has(data.status)) startPoll();
    } catch {
      alert('마케팅 잡을 불러오지 못했습니다.');
      router.push('/admin/marketing');
    } finally {
      setLoading(false);
    }
  };

  const handlePublish = async () => {
    if (!job) return;
    if (!confirm('마케팅 콘텐츠를 지금 게시하시겠습니까?')) return;

    setPublishing(true);
    try {
      const updated = await publishMarketingJob(job.id);
      setJob(updated);
      // Start polling until the dispatcher finishes (PUBLISHING → terminal)
      startPoll();
    } catch {
      alert('게시 요청에 실패했습니다.');
    } finally {
      setPublishing(false);
    }
  };

  const handleRepublish = async () => {
    if (!job) return;
    if (!confirm('플랫폼 계정 설정 후 게시를 재시도하시겠습니까?')) return;

    setRepublishing(true);
    try {
      const updated = await republishMarketingJob(job.id);
      setJob(updated);
      startPoll();
    } catch {
      alert('게시 재시도에 실패했습니다.');
    } finally {
      setRepublishing(false);
    }
  };

  const handleRegenerate = async () => {
    if (!job) return;
    if (!confirm('영상 품질 검증을 다시 실행합니다. 통과한 새 잡은 즉시 자동 게시됩니다.')) return;

    setRegenerating(true);
    try {
      const replacement = await regenerateMarketingJob(job.id);
      router.push(`/admin/marketing/jobs/${replacement.id}`);
    } catch {
      alert('영상 재생성 요청에 실패했습니다. 활성 잡이 있는지 확인해주세요.');
    } finally {
      setRegenerating(false);
    }
  };

  if (loading) {
    return (
      <AdminSection title="마케팅 잡 상세">
        <Card className="p-6">
          <div className="text-center text-gray-500">로드 중...</div>
        </Card>
      </AdminSection>
    );
  }

  if (!job) {
    return (
      <AdminSection title="마케팅 잡 상세">
        <Card className="p-6">
          <div className="text-center text-gray-500">잡을 찾을 수 없습니다.</div>
        </Card>
      </AdminSection>
    );
  }

  const needsAuthExists = hasNeedsAuth(job);
  const canRepublish = ['PARTIAL', 'FAILED', 'PUBLISHED'].includes(job.status);
  const canRegenerate = job.status === 'FAILED' && Boolean(job.failureCode) &&
    /^(SIBOM_|VARIANT_|DURATION_|LAYOUT_)/.test(job.failureCode ?? '');

  return (
    <AdminSection title="마케팅 잡 상세">
      <div className="space-y-6">
        {/* NEEDS_AUTH 안내 배너 */}
        {needsAuthExists && (
          <div className="rounded-lg border border-amber-300 bg-amber-50 p-4 flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 mt-0.5 shrink-0" />
            <div className="flex-1">
              <p className="font-medium text-amber-800">플랫폼 계정 인증이 필요합니다</p>
              <p className="text-sm text-amber-700 mt-1">
                하나 이상의 플랫폼에서 인증 정보가 없어 게시에 실패했습니다.
                플랫폼 계정을 설정한 후 "게시 재시도"를 눌러주세요.
              </p>
              <Link href="/admin/marketing?tab=credentials">
                <Button size="sm" variant="outline" className="mt-2 border-amber-400 text-amber-800 hover:bg-amber-100">
                  플랫폼 계정 설정하기
                </Button>
              </Link>
            </div>
          </div>
        )}

        {/* 게시 진행 중 안내 */}
        {ACTIVE_STATUSES.has(job.status) && (
          <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 flex items-center gap-3">
            <RefreshCw className="w-4 h-4 text-blue-600 animate-spin" />
            <p className="text-sm text-blue-700">게시 처리 중... 자동으로 상태가 갱신됩니다.</p>
          </div>
        )}

        {/* 기본 정보 */}
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4">기본 정보</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium text-gray-600">잡 ID</label>
              <p className="text-lg font-mono">{job.id}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">사연 ID</label>
              <p className="text-lg font-mono">{job.postId}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">상태</label>
              <p className="mt-1">
                <Badge className={STATUS_COLORS[job.status] || 'bg-gray-200'}>
                  {job.status}
                </Badge>
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">단계</label>
              <p className="text-lg">{job.phase || '-'}</p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">진행률</label>
              <p className="text-lg">
                {typeof job.progress === 'number' ? `${Math.round(job.progress * 100)}%` : '-'}
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">자동 게시</label>
              <p className="text-lg">
                <Badge variant={job.autoPublish ? 'default' : 'outline'}>
                  {job.autoPublish ? '활성화' : '비활성화'}
                </Badge>
              </p>
            </div>
            {job.generationAttempt != null && (
              <div>
                <label className="text-sm font-medium text-gray-600">생성 시도</label>
                <p className="text-lg font-mono">{job.generationAttempt}회</p>
              </div>
            )}
            {job.actualDurationMs != null && (
              <div>
                <label className="text-sm font-medium text-gray-600">최종 영상 길이</label>
                <p className="text-lg font-mono">{(job.actualDurationMs / 1000).toFixed(1)}초</p>
              </div>
            )}
            <div>
              <label className="text-sm font-medium text-gray-600">생성일</label>
              <p className="text-lg">
                {new Date(job.createdAt).toLocaleDateString('ko-KR', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </div>
            <div>
              <label className="text-sm font-medium text-gray-600">마지막 업데이트</label>
              <p className="text-lg">
                {new Date(job.updatedAt).toLocaleDateString('ko-KR', {
                  year: 'numeric',
                  month: 'long',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
            </div>
          </div>
        </Card>

        {/* 타겟 플랫폼 */}
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4">타겟 플랫폼</h3>
          <div className="flex flex-wrap gap-2">
            {(job.targets ?? []).map((target) => (
              <Badge key={target} variant="secondary">
                {target}
              </Badge>
            ))}
          </div>
        </Card>

        {/* 아티팩트 */}
        {job.artifacts && Object.keys(job.artifacts).length > 0 && (
          <ArtifactSection jobId={job.id} artifacts={job.artifacts} onArtifactsChanged={loadJob} />
        )}

        {/* 게시 기록 */}
        {job.publications && job.publications.length > 0 && (
          <Card className="p-6">
            <h3 className="text-lg font-semibold mb-4">게시 기록</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="text-left py-2 px-3 font-medium">플랫폼</th>
                    <th className="text-left py-2 px-3 font-medium">상태</th>
                    <th className="text-left py-2 px-3 font-medium">URL</th>
                  </tr>
                </thead>
                <tbody>
                  {job.publications.map((pub, idx) => (
                    <tr key={idx} className="border-b">
                      <td className="py-2 px-3 font-mono">{pub.platform}</td>
                      <td className="py-2 px-3">
                        <Badge className={PUB_STATE_COLORS[pub.state] || 'bg-gray-200 text-gray-800'}>
                          {pub.state === 'NEEDS_AUTH' ? '인증 필요' : pub.state}
                        </Badge>
                      </td>
                      <td className="py-2 px-3">
                        {pub.url && pub.url.startsWith('http') ? (
                          <a
                            href={pub.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-blue-600 hover:underline flex items-center gap-1"
                          >
                            링크
                            <ExternalLink className="w-3 h-3" />
                          </a>
                        ) : (
                          <span className="text-gray-400">-</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        )}

        {/* 오류 메시지 */}
        {(job.errorMessage || job.errorSummary || job.failureCode) && (
          <Card className="p-6 bg-red-50 border-red-200">
            <h3 className="text-lg font-semibold mb-4 text-red-800">오류</h3>
            {job.failureCode && (
              <p className="mb-2 font-mono text-sm font-semibold text-red-800">{job.failureCode}</p>
            )}
            {(job.failureStage || job.retryable !== null && job.retryable !== undefined) && (
              <p className="mb-2 text-sm text-red-700">
                {job.failureStage ? `단계: ${job.failureStage}` : ''}
                {job.failureStage && job.retryable !== null && job.retryable !== undefined ? ' · ' : ''}
                {job.retryable !== null && job.retryable !== undefined
                  ? `재생성 가능: ${job.retryable ? '예' : '아니오'}` : ''}
              </p>
            )}
            <p className="text-red-700 font-mono text-sm">{job.errorSummary ?? job.errorMessage}</p>
          </Card>
        )}

        {job.generationDiagnostics && Object.keys(job.generationDiagnostics).length > 0 && (
          <Card className="p-6">
            <h3 className="text-lg font-semibold mb-3">영상 품질 진단</h3>
            <pre className="max-h-72 overflow-auto rounded bg-slate-950 p-4 text-xs leading-5 text-slate-100">
              {JSON.stringify(job.generationDiagnostics, null, 2)}
            </pre>
          </Card>
        )}

        {/* 유입 통계 */}
        {traffic && (
          <Card className="p-6 bg-blue-50 border border-blue-200">
            <h3 className="text-lg font-semibold mb-4 text-blue-900">유입 통계</h3>
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-sm text-blue-700">총 방문</p>
                  <p className="text-2xl font-mono font-semibold text-blue-900">
                    {traffic.visits}회
                  </p>
                </div>
                <div>
                  <p className="text-sm text-blue-700">고유 세션</p>
                  <p className="text-2xl font-mono font-semibold text-blue-900">
                    {traffic.uniqueSessions}개
                  </p>
                </div>
              </div>
              {traffic.bySources.length > 0 && (
                <div className="border-t pt-3">
                  <p className="text-sm font-medium text-blue-800 mb-2">출처별 방문</p>
                  <div className="space-y-1">
                    {traffic.bySources.map((source) => (
                      <div
                        key={source.source}
                        className="flex justify-between items-center text-sm"
                      >
                        <span className="text-blue-700">{source.source}</span>
                        <span className="font-mono text-blue-900">{source.visits}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </Card>
        )}

        {/* 액션 버튼 */}
        <div className="flex gap-2 justify-end">
          <Link href="/admin/marketing">
            <Button variant="outline">돌아가기</Button>
          </Link>

          {/* 게시 재시도 — PARTIAL/FAILED/PUBLISHED 잡에 노출 */}
          {canRepublish && (
            <Button
              variant="outline"
              onClick={handleRepublish}
              disabled={republishing || ACTIVE_STATUSES.has(job.status)}
            >
              {republishing ? '재시도 중...' : '게시 재시도'}
            </Button>
          )}

          {canRegenerate && (
            <Button
              variant="default"
              onClick={handleRegenerate}
              disabled={regenerating}
            >
              {regenerating ? '재생성 중...' : '영상 재생성'}
            </Button>
          )}

          {/* 최초 게시 승인 — READY이고 자동게시 꺼진 경우 */}
          {job.status === 'READY' && !job.autoPublish && (
            <Button onClick={handlePublish} disabled={publishing}>
              {publishing ? '게시 중...' : '게시 승인'}
            </Button>
          )}
        </div>
      </div>
    </AdminSection>
  );
}
