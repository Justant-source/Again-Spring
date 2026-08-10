'use client';

import { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { AdminTable } from '@/components/admin/AdminTable';
import type {
  MarketingHoldingRow,
  MarketingProjectedFormat,
  MarketingHoldingStatus,
  MarketingPinFormat,
} from '@/lib/api/admin/marketing';

export interface HoldingBoardProps {
  rows: MarketingHoldingRow[];
  loading?: boolean;
  /** Auto cutline N (remaining shared pool). Rows with rank > cutline are dimmed. */
  cutline?: number;
  /** Max rows to render (board displays up to 20). */
  maxRows?: number;
  onEdit?: (row: MarketingHoldingRow) => void;
  /** Open content-management style story dialog for this post. */
  onOpenPost?: (row: MarketingHoldingRow) => void;
  onPin?: (row: MarketingHoldingRow, format: MarketingPinFormat) => void;
  onUnpin?: (row: MarketingHoldingRow) => void;
  className?: string;
}

const FORMAT_LABEL: Record<MarketingProjectedFormat, string> = {
  VIDEO: '영상',
  TEXT: '글',
  OUT_OF_CUT: '컷외',
};

const STATUS_LABEL: Record<MarketingHoldingStatus, string> = {
  IN_POOL: '후보',
  PINNED: '핀',
  OUT_OF_CUT: '후보 외',
  COMMITTED: '확정',
  DROPPED: '탈락',
};

function formatTimeTo24h(postCreatedAt: string): string {
  const created = new Date(postCreatedAt).getTime();
  if (Number.isNaN(created)) return '—';
  const ms = created + 24 * 60 * 60 * 1000 - Date.now();
  if (ms <= 0) return '만료';
  const totalMin = Math.floor(ms / 60_000);
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  if (h >= 24) return `${h}시간+`;
  return `${h}시간 ${m}분`;
}

function formatScoreTooltip(row: MarketingHoldingRow): string {
  return `조회 ${row.viewCount} · 댓글 ${row.commentCount} · 투표 ${row.voteCount}`;
}

function statusBadgeClass(status: MarketingHoldingStatus): string {
  switch (status) {
    case 'PINNED':
      return 'bg-amber-100 text-amber-900';
    case 'OUT_OF_CUT':
      return 'bg-gray-200 text-gray-600';
    case 'COMMITTED':
      return 'bg-green-100 text-green-800';
    case 'DROPPED':
      return 'bg-red-100 text-red-800';
    default:
      return 'bg-blue-100 text-blue-800';
  }
}

function formatBadgeClass(format: MarketingProjectedFormat): string {
  switch (format) {
    case 'VIDEO':
      return 'bg-violet-100 text-violet-800';
    case 'TEXT':
      return 'bg-sky-100 text-sky-800';
    default:
      return 'bg-gray-100 text-gray-500';
  }
}

export function HoldingBoard({
  rows,
  loading = false,
  cutline,
  maxRows = 20,
  onEdit,
  onOpenPost,
  onPin,
  onUnpin,
  className,
}: HoldingBoardProps) {
  const visible = rows.slice(0, maxRows);
  const canEdit = typeof onEdit === 'function';
  const canOpenPost = typeof onOpenPost === 'function';
  const canPin = typeof onPin === 'function';
  const canUnpin = typeof onUnpin === 'function';
  const [pinPickerRowId, setPinPickerRowId] = useState<string | null>(null);

  const handlePinFormatSelect = (row: MarketingHoldingRow, format: MarketingPinFormat) => {
    setPinPickerRowId(null);
    onPin?.(row, format);
  };

  return (
    <div className={className} data-testid="marketing-holding-board">
      <div className="bg-white rounded-lg border">
        <AdminTable<MarketingHoldingRow>
          data={visible}
          loading={loading}
          emptyMessage="대기 홀딩이 없습니다. 표시 보드(최대 20)에 진입하면 여기에 나타납니다."
          rowKey={(row) => row.postId}
          rowTestId={(row) => `holding-row-${row.postId}`}
          columns={[
            {
              key: 'rank',
              header: '순위',
              render: (row) => {
                const rank = row.rankSnapshot;
                const belowCut =
                  cutline != null && rank != null && rank > cutline;
                return (
                  <span
                    className={
                      belowCut ? 'text-gray-400 font-mono' : 'font-mono font-medium'
                    }
                  >
                    {rank ?? '—'}
                  </span>
                );
              },
            },
            {
              key: 'score',
              header: '점수',
              render: (row) => (
                <span
                  className="font-mono text-sm"
                  title={formatScoreTooltip(row)}
                >
                  {Number.isFinite(row.scoreSnapshot)
                    ? row.scoreSnapshot.toFixed(1)
                    : '—'}
                </span>
              ),
            },
            {
              key: 'title',
              header: '사연',
              render: (row) => {
                const belowCut =
                  cutline != null &&
                  row.rankSnapshot != null &&
                  row.rankSnapshot > cutline;
                const title = row.title || '(제목 없음)';
                return (
                  <div className={belowCut ? 'opacity-60' : ''}>
                    {canOpenPost ? (
                      <button
                        type="button"
                        className="font-medium text-blue-700 hover:underline text-left"
                        data-testid={`holding-row-title-${row.postId}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          onOpenPost(row);
                        }}
                      >
                        {title}
                      </button>
                    ) : (
                      <span
                        className="font-medium text-gray-800"
                        data-testid={`holding-row-title-${row.postId}`}
                      >
                        {title}
                      </span>
                    )}
                  </div>
                );
              },
            },
            {
              key: 'timeTo24h',
              header: 'T+24h까지',
              render: (row) => (
                <span className="text-sm text-gray-700 whitespace-nowrap">
                  {formatTimeTo24h(row.postCreatedAt)}
                </span>
              ),
            },
            {
              key: 'projectedFormat',
              header: '포맷',
              render: (row) => (
                <Badge className={formatBadgeClass(row.projectedFormat)}>
                  {FORMAT_LABEL[row.projectedFormat] ?? row.projectedFormat}
                </Badge>
              ),
            },
            {
              key: 'status',
              header: '상태',
              render: (row) => (
                <div className="flex flex-wrap gap-1 items-center">
                  <Badge className={statusBadgeClass(row.status)}>
                    {STATUS_LABEL[row.status] ?? row.status}
                    {row.status === 'PINNED' && row.pinFormat
                      ? ` (${row.pinFormat === 'VIDEO' ? '영상' : '글'})`
                      : ''}
                  </Badge>
                  {row.lockedAt && (
                    <Badge variant="outline" className="text-xs">
                      잠금
                    </Badge>
                  )}
                </div>
              ),
            },
            {
              key: 'actions',
              header: '액션',
              render: (row) => {
                const pinned = row.status === 'PINNED';
                const actionable =
                  row.status === 'IN_POOL' ||
                  row.status === 'PINNED' ||
                  row.status === 'OUT_OF_CUT';
                const pickingFormat = pinPickerRowId === row.postId;
                return (
                  <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={!canEdit || !actionable}
                      onClick={() => onEdit?.(row)}
                      data-testid={`holding-edit-${row.postId}`}
                    >
                      초안
                    </Button>
                    {pinned ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        disabled={!canUnpin || !actionable}
                        onClick={() => onUnpin?.(row)}
                        data-testid={`holding-unpin-${row.postId}`}
                      >
                        핀 해제
                      </Button>
                    ) : pickingFormat ? (
                      <div className="flex items-center gap-1">
                        {/*
                          No defaultValue: Radix skips onValueChange when the clicked
                          option equals the current value. After VIDEO slots appear on
                          the board, defaulting to VIDEO made 「영상」 a no-op and the
                          pin never stuck (e2e 13-F).
                        */}
                        <Select
                          onValueChange={(value) =>
                            handlePinFormatSelect(row, value as MarketingPinFormat)
                          }
                        >
                          <SelectTrigger
                            className="h-8 w-24 text-sm"
                            data-testid={`holding-pin-format-select-${row.postId}`}
                          >
                            <SelectValue placeholder="포맷" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="VIDEO">영상</SelectItem>
                            <SelectItem value="TEXT">글</SelectItem>
                          </SelectContent>
                        </Select>
                        <Button
                          type="button"
                          size="sm"
                          variant="ghost"
                          onClick={() => setPinPickerRowId(null)}
                          data-testid={`holding-pin-cancel-${row.postId}`}
                        >
                          취소
                        </Button>
                      </div>
                    ) : (
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        disabled={!canPin || !actionable}
                        onClick={() => setPinPickerRowId(row.postId)}
                        data-testid={`holding-pin-${row.postId}`}
                      >
                        핀
                      </Button>
                    )}
                  </div>
                );
              },
            },
          ]}
        />
      </div>
    </div>
  );
}
