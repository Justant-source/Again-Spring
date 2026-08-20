'use client';

import { useEffect, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { AdminTable } from '@/components/admin/AdminTable';
import {
  redriveMarketingJobs,
  type RedriveRequest,
  type RedriveResponse,
  type RedriveResult,
} from '@/lib/api/admin/marketing';

export interface JobRedriveDialogProps {
  open: boolean;
  failedJobIds: number[];
  loading?: boolean;
  error?: string | null;
  onClose: () => void;
  onSuccess?: (response: RedriveResponse) => void;
}

const ACTION_LABEL: Record<string, string> = {
  REGENERATED: '재생성됨',
  RECREATED: '재생성됨(생성)',
  SKIPPED: '스킵됨',
  ERROR: '오류',
};

const ACTION_COLOR: Record<string, string> = {
  REGENERATED: 'bg-blue-100 text-blue-800',
  RECREATED: 'bg-blue-100 text-blue-800',
  SKIPPED: 'bg-gray-100 text-gray-800',
  ERROR: 'bg-red-100 text-red-800',
};

function ResultRow({ result }: { result: RedriveResult }) {
  const platformEntries = result.platformStates
    ? Object.entries(result.platformStates)
    : [];

  return (
    <div className="text-sm">
      {result.targetId && (
        <div className="font-mono text-gray-600">
          {result.sourceId} → {result.targetId}
        </div>
      )}
      {!result.targetId && (
        <div className="font-mono text-gray-600">{result.sourceId}</div>
      )}
      {platformEntries.length > 0 && (
        <div className="mt-1 flex flex-wrap gap-1">
          {platformEntries.map(([platform, state]) => (
            <Badge
              key={`${result.sourceId}-${platform}`}
              variant="outline"
              className="text-xs"
            >
              {platform}: {String(state)}
            </Badge>
          ))}
        </div>
      )}
    </div>
  );
}

export function JobRedriveDialog({
  open,
  failedJobIds,
  loading = false,
  error = null,
  onClose,
  onSuccess,
}: JobRedriveDialogProps) {
  const [executing, setExecuting] = useState(false);
  const [results, setResults] = useState<RedriveResponse | null>(null);
  const [execError, setExecError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setResults(null);
      setExecError(null);
    }
  }, [open]);

  const handleExecute = async () => {
    if (!failedJobIds.length || executing) return;
    setExecuting(true);
    setExecError(null);
    try {
      const request: RedriveRequest = {
        jobIds: failedJobIds,
        skipExisting: true,
      };
      const response = await redriveMarketingJobs(request);
      setResults(response);
      onSuccess?.(response);
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message
          : typeof err === 'object' && err !== null && 'response' in err
            ? (err as any).response?.data?.message || String(err)
            : String(err);
      setExecError(`재구동 요청 실패: ${msg}`);
    } finally {
      setExecuting(false);
    }
  };

  if (results) {
    // Show results view
    return (
      <Dialog open={open} onOpenChange={onClose}>
        <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>재구동 결과</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div className="rounded bg-blue-50 px-4 py-3 text-sm text-blue-800">
              {results.requested}개 잡 중 {results.results.length}개 결과 반환됨
            </div>

            {results.results.length > 0 && (
              <div
                className="bg-white rounded border"
                data-testid="completed-redrive-results-table"
              >
                <AdminTable
                  data={results.results}
                  loading={false}
                  emptyMessage="결과 없음"
                  rowKey={(row) => `${row.sourceId}-${row.targetId}`}
                  rowTestId={(row) => `redrive-result-row-${row.sourceId}`}
                  columns={[
                    {
                      key: 'source',
                      header: '원본 ID',
                      render: (row) => (
                        <span className="font-mono text-sm">
                          {row.sourceId}
                        </span>
                      ),
                    },
                    {
                      key: 'target',
                      header: '자식 ID',
                      render: (row) => (
                        <span className="font-mono text-sm">
                          {row.targetId || '—'}
                        </span>
                      ),
                    },
                    {
                      key: 'action',
                      header: '취한 조치',
                      render: (row) => (
                        <Badge
                          className={ACTION_COLOR[row.action] || 'bg-gray-100'}
                        >
                          {ACTION_LABEL[row.action] || row.action}
                        </Badge>
                      ),
                    },
                    {
                      key: 'reason',
                      header: '상세',
                      render: (row) => (
                        <div className="text-sm text-gray-600">
                          {row.reason || '—'}
                        </div>
                      ),
                    },
                    {
                      key: 'platforms',
                      header: '플랫폼 상태',
                      render: (row) => <ResultRow result={row} />,
                    },
                  ]}
                />
              </div>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={onClose}>
              닫기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  // Show confirmation view
  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent data-testid="completed-redrive-dialog">
        <DialogHeader>
          <DialogTitle>실패 잡 일괄 재구동</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {error && (
            <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </div>
          )}

          {execError && (
            <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              {execError}
            </div>
          )}

          {!failedJobIds.length ? (
            <div className="rounded bg-yellow-50 px-4 py-3 text-sm text-yellow-800">
              실패한 잡이 없습니다.
            </div>
          ) : (
            <div
              className="rounded bg-blue-50 px-4 py-3 text-sm text-blue-800"
              data-testid="completed-redrive-summary"
            >
              <strong>{failedJobIds.length}개</strong>의 실패한 잡을 재구동합니다.
              <br />
              이미 일부 플랫폼에 게시된 잡은 해당 플랫폼 재발행을 스킵합니다.
            </div>
          )}

          {failedJobIds.length > 0 && (
            <div className="rounded bg-gray-50 px-3 py-2 max-h-40 overflow-y-auto">
              <div className="text-xs text-gray-600 font-mono space-y-1">
                {failedJobIds.map((jobId) => (
                  <div key={jobId}>Job #{jobId}</div>
                ))}
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={onClose}
            disabled={executing}
            data-testid="completed-redrive-cancel-btn"
          >
            취소
          </Button>
          <Button
            onClick={handleExecute}
            disabled={!failedJobIds.length || executing || loading}
            data-testid="completed-redrive-execute-btn"
          >
            {executing ? '재구동 중…' : '재구동 실행'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
