'use client';

import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { AdminTable } from '@/components/admin/AdminTable';
import type { CompletedHoldingView } from '@/components/admin/marketing/CompletedHoldingsBoard';
import { redriveMarketingJobs, type RedriveResponse } from '@/lib/api/admin/marketing';

export interface FailedJobsBoardProps {
  items: CompletedHoldingView[];
  loading?: boolean;
  onRedriveSuccess?: (response: RedriveResponse) => void;
  className?: string;
}

/** Extract all failed jobs from completed items with their source holding info. */
function extractFailedJobs(
  items: CompletedHoldingView[]
): Array<{
  jobId: number;
  jobStatus: string;
  postId: string;
  postTitle: string | null;
  sourceStatus: string;
  createdAt: string;
}> {
  const failed: Array<{
    jobId: number;
    jobStatus: string;
    postId: string;
    postTitle: string | null;
    sourceStatus: string;
    createdAt: string;
  }> = [];
  for (const item of items) {
    for (const job of item.jobs ?? []) {
      if (job.status === 'FAILED') {
        failed.push({
          jobId: job.id,
          jobStatus: job.status,
          postId: item.postId,
          postTitle: item.title ?? null,
          sourceStatus: item.status,
          createdAt: job.createdAt || item.createdAt,
        });
      }
    }
  }
  return failed.sort((a, b) => b.jobId - a.jobId);
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function JobRedriveControl({
  jobId,
  onSuccess,
}: {
  jobId: number;
  onSuccess?: (response: RedriveResponse) => void;
}) {
  const [executing, setExecuting] = useState(false);

  const handleRedrive = async () => {
    setExecuting(true);
    try {
      const response = await redriveMarketingJobs({
        jobIds: [jobId],
        skipExisting: true,
      });
      onSuccess?.(response);
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : typeof err === 'object' && err !== null && 'response' in err
            ? (err as any).response?.data?.message || String(err)
            : String(err);
      alert(`재구동 실패: ${msg}`);
    } finally {
      setExecuting(false);
    }
  };

  return (
    <Button
      type="button"
      size="sm"
      variant="outline"
      disabled={executing}
      onClick={handleRedrive}
      data-testid={`job-redrive-btn-${jobId}`}
    >
      {executing ? '재구동 중…' : '재구동'}
    </Button>
  );
}

export function FailedJobsBoard({
  items,
  loading = false,
  onRedriveSuccess,
  className,
}: FailedJobsBoardProps) {
  const failedJobs = extractFailedJobs(items);

  if (failedJobs.length === 0) {
    return null;
  }

  return (
    <div className={className}>
      <div className="mb-8">
        <h3 className="font-semibold text-gray-800 mb-4">
          실패한 잡 ({failedJobs.length})
        </h3>
        <div className="bg-white rounded-lg border">
          <AdminTable
            data={failedJobs}
            loading={loading}
            emptyMessage="실패한 잡이 없습니다."
            rowKey={(row) => `job-${row.jobId}`}
            rowTestId={(row) => `failed-job-row-${row.jobId}`}
            columns={[
              {
                key: 'jobId',
                header: '잡 ID',
                render: (row) => (
                  <span className="font-mono text-sm font-medium">
                    #{row.jobId}
                  </span>
                ),
              },
              {
                key: 'title',
                header: '사연',
                render: (row) => (
                  <div className="text-sm">
                    <div className="font-medium text-gray-800">
                      {row.postTitle || '(제목 없음)'}
                    </div>
                    <div className="text-xs text-gray-500 font-mono">
                      {row.postId}
                    </div>
                  </div>
                ),
              },
              {
                key: 'status',
                header: '상태',
                render: (row) => (
                  <div className="text-sm">
                    <Badge className="bg-red-100 text-red-800 mb-1">
                      {row.jobStatus}
                    </Badge>
                    <div className="text-xs text-gray-600">
                      (holdings: {row.sourceStatus})
                    </div>
                  </div>
                ),
              },
              {
                key: 'createdAt',
                header: '생성 시각',
                render: (row) => (
                  <span className="text-sm text-gray-700 whitespace-nowrap">
                    {formatDateTime(row.createdAt)}
                  </span>
                ),
              },
              {
                key: 'actions',
                header: '재구동',
                render: (row) => (
                  <JobRedriveControl
                    jobId={row.jobId}
                    onSuccess={onRedriveSuccess}
                  />
                ),
              },
            ]}
          />
        </div>
      </div>
    </div>
  );
}
