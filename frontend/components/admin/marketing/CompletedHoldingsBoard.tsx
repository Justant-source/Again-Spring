'use client';

import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { AdminTable } from '@/components/admin/AdminTable';
import type {
  MarketingCompletedItem,
  MarketingForceMode,
} from '@/lib/api/admin/marketing';

/**
 * Completed-tab holding view consumed by this board. Currently an alias of
 * `MarketingCompletedItem` (see `marketing.ts` — `title`/`format`/
 * `jobs[].publications` were added there additively for the 완료 탭 redesign).
 * Kept as a named export so callers can depend on this shape without coupling
 * to the underlying API type name.
 */
export type CompletedHoldingView = MarketingCompletedItem;

export interface CompletedHoldingsBoardProps {
  items: CompletedHoldingView[];
  loading?: boolean;
  /** Fired on row click for a COMMITTED (게시 이력) row — opens the read-only publication dialog. */
  onRowClick?: (item: CompletedHoldingView) => void;
  /** Fired on DROPPED row click / title — opens content story dialog. */
  onOpenDroppedPost?: (item: CompletedHoldingView) => void;
  /** Executes a force-deploy for a DROPPED item once the operator confirms a mode. */
  onForce?: (postId: string, mode: MarketingForceMode) => void | Promise<void>;
  /** postId currently executing a force-deploy request (disables its row controls). */
  forceBusyPostId?: string | null;
  className?: string;
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
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Dedupe per-platform state across a completed item's jobs (last write wins). */
function summarizePlatforms(
  item: CompletedHoldingView
): Array<{ platform: string; state: string }> {
  const byPlatform = new Map<string, string>();
  for (const job of item.jobs ?? []) {
    const publications = job.publications ?? [];
    if (publications.length > 0) {
      for (const pub of publications) {
        byPlatform.set(pub.platform, pub.state);
      }
    } else {
      for (const target of job.targets ?? []) {
        if (!byPlatform.has(target)) byPlatform.set(target, job.status);
      }
    }
  }
  return Array.from(byPlatform.entries()).map(([platform, state]) => ({
    platform,
    state,
  }));
}

function PlatformSummary({ item }: { item: CompletedHoldingView }) {
  const summary = summarizePlatforms(item);
  if (summary.length === 0) {
    return <span className="text-xs text-gray-400">—</span>;
  }
  return (
    <div className="flex flex-wrap gap-1">
      {summary.map(({ platform, state }) => (
        <Badge
          key={platform}
          className={PUBLICATION_STATE_COLOR[state] || 'bg-gray-100 text-gray-600'}
          title={`${PLATFORM_LABELS[platform] ?? platform}: ${state}`}
        >
          {PLATFORM_LABELS[platform] ?? platform}
        </Badge>
      ))}
    </div>
  );
}

function ForceDeployControl({
  item,
  busy,
  onForce,
}: {
  item: CompletedHoldingView;
  busy: boolean;
  onForce?: (postId: string, mode: MarketingForceMode) => void | Promise<void>;
}) {
  const [mode, setMode] = useState<MarketingForceMode>('TEXT_ONLY');
  const [executing, setExecuting] = useState(false);
  const disabled = !onForce || busy || executing;

  const handleExecute = async () => {
    if (!onForce || disabled) return;
    setExecuting(true);
    try {
      await onForce(item.postId, mode);
    } finally {
      setExecuting(false);
    }
  };

  return (
    <div
      className="flex items-center gap-2"
      data-testid={`completed-force-${item.postId}`}
      onClick={(e) => e.stopPropagation()}
    >
      <select
        className="h-8 rounded-md border border-input bg-background px-2 text-xs disabled:opacity-50"
        value={mode}
        disabled={disabled}
        onChange={(e) => setMode(e.target.value as MarketingForceMode)}
        data-testid={`completed-force-mode-${item.postId}`}
        aria-label="강제 배포 모드"
      >
        <option value="VIDEO_AND_TEXT">영상+글</option>
        <option value="TEXT_ONLY">글만</option>
      </select>
      <Button
        type="button"
        size="sm"
        variant="outline"
        disabled={disabled}
        onClick={handleExecute}
        data-testid={`completed-force-execute-${item.postId}`}
      >
        {executing || busy ? '배포 중…' : '강제 배포'}
      </Button>
    </div>
  );
}

export function CompletedHoldingsBoard({
  items,
  loading = false,
  onRowClick,
  onOpenDroppedPost,
  onForce,
  forceBusyPostId = null,
  className,
}: CompletedHoldingsBoardProps) {
  const published = items.filter((item) => item.status === 'COMMITTED');
  const dropped = items.filter((item) => item.status === 'DROPPED');

  return (
    <div className={className}>
      <div className="mb-8" data-testid="marketing-completed-published-board">
        <h3 className="font-semibold text-gray-800 mb-4">게시 이력</h3>
        <div className="bg-white rounded-lg border">
          <AdminTable<CompletedHoldingView>
            data={published}
            loading={loading}
            emptyMessage="확정된 게시 이력이 없습니다."
            rowKey={(row) => row.postId}
            rowTestId={(row) => `completed-published-row-${row.postId}`}
            onRowClick={onRowClick}
            columns={[
              {
                key: 'title',
                header: '사연',
                render: (row) => (
                  <div className="font-medium text-gray-800">
                    {row.title || '(제목 없음)'}
                  </div>
                ),
              },
              {
                key: 'lockedAt',
                header: '확정 시각',
                render: (row) => (
                  <span className="text-sm text-gray-700 whitespace-nowrap">
                    {formatDateTime(row.lockedAt ?? row.updatedAt)}
                  </span>
                ),
              },
              {
                key: 'format',
                header: '포맷',
                render: (row) => {
                  const format = row.format ?? row.pinFormat ?? null;
                  return (
                    <span className="text-sm text-gray-700">
                      {format ? FORMAT_LABEL[format] ?? format : '—'}
                    </span>
                  );
                },
              },
              {
                key: 'platforms',
                header: '플랫폼 요약',
                render: (row) => <PlatformSummary item={row} />,
              },
            ]}
          />
        </div>
      </div>

      <div data-testid="marketing-completed-dropped-board">
        <h3 className="font-semibold text-gray-800 mb-4">탈락</h3>
        <div className="bg-white rounded-lg border">
          <AdminTable<CompletedHoldingView>
            data={dropped}
            loading={loading}
            emptyMessage="탈락한 홀딩이 없습니다."
            rowKey={(row) => row.postId}
            rowTestId={(row) => `completed-dropped-row-${row.postId}`}
            onRowClick={onOpenDroppedPost}
            columns={[
              {
                key: 'title',
                header: '사연',
                render: (row) => (
                  <button
                    type="button"
                    className="font-medium text-blue-700 hover:underline text-left"
                    data-testid={`completed-dropped-title-${row.postId}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      onOpenDroppedPost?.(row);
                    }}
                  >
                    {row.title || '(제목 없음)'}
                  </button>
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
                key: 'updatedAt',
                header: '탈락 시각',
                render: (row) => (
                  <span className="text-sm text-gray-700 whitespace-nowrap">
                    {formatDateTime(row.updatedAt)}
                  </span>
                ),
              },
              {
                key: 'actions',
                header: '강제 배포',
                render: (row) => (
                  <ForceDeployControl
                    item={row}
                    busy={forceBusyPostId === row.postId}
                    onForce={onForce}
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
