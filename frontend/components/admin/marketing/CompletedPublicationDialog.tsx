'use client';

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import { ExternalLink } from 'lucide-react';
import type { CompletedHoldingView } from './CompletedHoldingsBoard';

export interface CompletedPublicationDialogProps {
  open: boolean;
  /** COMMITTED holding clicked on the 게시 이력 board. Null while closed / no selection. */
  item: CompletedHoldingView | null;
  onClose: () => void;
}

const FORMAT_LABEL: Record<string, string> = {
  VIDEO: '영상',
  TEXT: '글',
};

const PLATFORM_LABELS: Record<string, string> = {
  naver_blog: '네이버 블로그',
  x_thread: 'X 스레드',
  instagram_feed: '인스타그램 피드',
  instagram_reels: '인스타그램 릴스',
  youtube_shorts: 'YouTube Shorts',
  naver_clip: '네이버 클립',
  threads: 'Threads',
};

const JOB_STATUS_COLOR: Record<string, string> = {
  PUBLISHED: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  PARTIAL: 'bg-yellow-500 text-white',
  READY: 'bg-green-100 text-green-800',
  RUNNING: 'bg-yellow-200 text-yellow-800',
  PUBLISHING: 'bg-orange-100 text-orange-800',
  STALE: 'bg-gray-400 text-white',
};

const PUBLICATION_STATE_COLOR: Record<string, string> = {
  PUBLISHED: 'bg-green-100 text-green-800',
  FAILED: 'bg-red-100 text-red-800',
  NEEDS_AUTH: 'bg-red-100 text-red-800',
  PENDING: 'bg-blue-100 text-blue-800',
  PUBLISHING: 'bg-orange-100 text-orange-800',
  PARTIAL: 'bg-yellow-500 text-white',
  MANUAL: 'bg-gray-200 text-gray-700',
};

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function CompletedPublicationDialog({
  open,
  item,
  onClose,
}: CompletedPublicationDialogProps) {
  const format = item?.format ?? item?.pinFormat ?? null;

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) onClose();
      }}
    >
      <DialogContent
        className="sm:max-w-lg max-h-[90vh] overflow-y-auto"
        data-testid="marketing-completed-publication-dialog"
      >
        <DialogHeader>
          <DialogTitle>{item?.title || '(제목 없음)'}</DialogTitle>
        </DialogHeader>

        {item && (
          <div className="space-y-4 py-1">
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <p className="text-xs text-gray-500">사연 ID</p>
                <p className="font-mono">{item.postId}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">포맷</p>
                <p>{format ? FORMAT_LABEL[format] ?? format : '—'}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">확정 시각</p>
                <p>{formatDateTime(item.lockedAt ?? item.updatedAt)}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500">점수</p>
                <p className="font-mono">
                  {item.scoreSnapshot != null
                    ? Number(item.scoreSnapshot).toFixed(1)
                    : '—'}
                </p>
              </div>
            </div>

            <div className="space-y-3" data-testid="completed-publication-jobs">
              {(item.jobs ?? []).length === 0 && (
                <p className="text-sm text-gray-400">연결된 잡이 없습니다.</p>
              )}
              {(item.jobs ?? []).map((job) => (
                <div key={job.id} className="rounded border p-3" data-testid={`completed-publication-job-${job.id}`}>
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono text-xs text-gray-500">Job {job.id}</span>
                    <Badge
                      className={JOB_STATUS_COLOR[job.status] || 'bg-gray-100 text-gray-700'}
                      data-testid="job-status-badge"
                      data-status={job.status}
                    >
                      {job.status}
                    </Badge>
                  </div>
                  {(job.publications ?? []).length > 0 ? (
                    <div className="space-y-1.5">
                      {(job.publications ?? []).map((pub, idx) => (
                        <div
                          key={`${pub.platform}-${idx}`}
                          className="flex items-center justify-between text-sm"
                        >
                          <span>{PLATFORM_LABELS[pub.platform] ?? pub.platform}</span>
                          <div className="flex items-center gap-2">
                            <Badge
                              className={
                                PUBLICATION_STATE_COLOR[pub.state] || 'bg-gray-100 text-gray-600'
                              }
                            >
                              {pub.state === 'NEEDS_AUTH' ? '인증 필요' : pub.state}
                            </Badge>
                            {pub.url && pub.url.startsWith('http') && (
                              <a
                                href={pub.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-blue-600 hover:underline flex items-center gap-1"
                              >
                                링크
                                <ExternalLink className="w-3 h-3" />
                              </a>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="flex flex-wrap gap-1">
                      {(job.targets ?? []).map((target) => (
                        <Badge key={target} variant="secondary" className="text-xs">
                          {PLATFORM_LABELS[target] ?? target}
                        </Badge>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
