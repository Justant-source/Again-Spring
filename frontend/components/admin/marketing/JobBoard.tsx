'use client';

import { useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { MarketingJob } from '@/lib/api/admin/marketing';

interface JobBoardProps {
  jobs: MarketingJob[];
  onPublish: (id: number) => Promise<void>;
  onRepublish: (id: number) => Promise<void>;
}

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

export function JobBoard({ jobs, onPublish, onRepublish }: JobBoardProps) {
  const [publishingId, setPublishingId] = useState<number | null>(null);
  const [republishingId, setRepublishingId] = useState<number | null>(null);

  // Group jobs by category
  const inProgress = jobs.filter((j) =>
    ['REQUESTED', 'QUEUED', 'RUNNING', 'PUBLISHING'].includes(j.status)
  );

  const pendingApproval = jobs.filter(
    (j) => j.status === 'READY' && !j.autoPublish
  );

  const completed = jobs.filter((j) =>
    ['PUBLISHED', 'PARTIAL'].includes(j.status)
  );

  const failed = jobs.filter((j) => ['FAILED', 'STALE'].includes(j.status));

  const handlePublish = async (jobId: number) => {
    if (!confirm('마케팅 콘텐츠를 지금 게시하시겠습니까?')) return;
    setPublishingId(jobId);
    try {
      await onPublish(jobId);
    } finally {
      setPublishingId(null);
    }
  };

  const handleRepublish = async (jobId: number) => {
    if (!confirm('마케팅 콘텐츠를 재시도하시겠습니까?')) return;
    setRepublishingId(jobId);
    try {
      await onRepublish(jobId);
    } finally {
      setRepublishingId(null);
    }
  };

  const renderJobCard = (job: MarketingJob, showPublishBtn = false, showRepublishBtn = false) => (
    <Link key={job.id} href={`/admin/marketing/jobs/${job.id}`}>
      <div
        className="block p-4 rounded-lg border cursor-pointer hover:shadow-md transition-shadow"
      >
        <div className="flex items-start justify-between mb-3">
          <div className="flex-1">
            <p className="font-mono text-sm text-gray-600">Job {job.id}</p>
            <p className="font-mono text-xs text-gray-500 mt-1">{job.postId}</p>
          </div>
          <Badge className={STATUS_COLORS[job.status] || 'bg-gray-200'}>
            {job.status}
          </Badge>
        </div>

        <div className="mb-3">
          <div className="flex items-center justify-between text-xs text-gray-600 mb-1">
            <span>진행률</span>
            <span>{typeof job.progress === 'number' ? `${Math.round(job.progress * 100)}%` : '-'}</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className="bg-blue-500 h-2 rounded-full transition-all"
              style={{
                width: `${typeof job.progress === 'number' ? Math.round(job.progress * 100) : 0}%`,
              }}
            />
          </div>
        </div>

        {(job.targets ?? []).length > 0 && (
          <div className="mb-3">
            <p className="text-xs text-gray-600 mb-1">플랫폼</p>
            <div className="flex flex-wrap gap-1">
              {(job.targets ?? []).map((target) => (
                <Badge key={target} variant="outline" className="text-xs">
                  {target}
                </Badge>
              ))}
            </div>
          </div>
        )}

        <div className="flex items-center justify-between text-xs text-gray-500">
          <span>
            {new Date(job.createdAt).toLocaleDateString('ko-KR', {
              month: 'short',
              day: 'numeric',
              hour: '2-digit',
              minute: '2-digit',
            })}
          </span>
          {(showPublishBtn || showRepublishBtn) && (
            <div onClick={(e) => e.preventDefault()} className="flex gap-1">
              {showPublishBtn && (
                <Button
                  size="sm"
                  variant="default"
                  onClick={(e) => {
                    e.preventDefault();
                    handlePublish(job.id);
                  }}
                  disabled={publishingId === job.id}
                  className="text-xs"
                >
                  {publishingId === job.id ? '게시 중...' : '게시하기'}
                </Button>
              )}
              {showRepublishBtn && (
                <Button
                  size="sm"
                  variant="outline"
                  onClick={(e) => {
                    e.preventDefault();
                    handleRepublish(job.id);
                  }}
                  disabled={republishingId === job.id}
                  className="text-xs"
                >
                  {republishingId === job.id ? '재시도 중...' : '재시도'}
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    </Link>
  );

  return (
    <div className="space-y-6" data-testid="marketing-job-board">
      {/* In Progress */}
      {inProgress.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-700 mb-3">진행 중</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {inProgress.map((job) => renderJobCard(job))}
          </div>
        </div>
      )}

      {/* Pending Approval */}
      {pendingApproval.length > 0 && (
        <div
          className="p-4 bg-amber-50 border border-amber-300 rounded-lg"
          data-testid="marketing-pending-approval"
        >
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-amber-900">
              검수 대기 <Badge variant="destructive">{pendingApproval.length}</Badge>
            </h3>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {pendingApproval.map((job) => renderJobCard(job, true))}
          </div>
        </div>
      )}

      {/* Completed */}
      {completed.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-700 mb-3">완료</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {completed.map((job) =>
              renderJobCard(job)
            )}
          </div>
        </div>
      )}

      {/* Failed */}
      {failed.length > 0 && (
        <div>
          <h3 className="text-sm font-semibold text-gray-700 mb-3">실패</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {failed.map((job) => renderJobCard(job, false, true))}
          </div>
        </div>
      )}

      {/* Empty state */}
      {jobs.length === 0 && (
        <Card className="p-12 text-center text-gray-400">
          <p>마케팅 잡이 없습니다.</p>
        </Card>
      )}
    </div>
  );
}
