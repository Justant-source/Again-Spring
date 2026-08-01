'use client';

import { ReactNode, useEffect, useRef } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { AlertCircle, Trash2 } from 'lucide-react';
import {
  applyPostAtDeltaToItems,
  fromDatetimeLocalKst,
  formatKstLabel,
} from './datetimeKst';
import { THREAD_CATEGORY_OPTIONS, type ThreadEditorItem, type ThreadEditorValue } from './types';

export interface ThreadEditorDialogProps {
  open: boolean;
  title: string;
  value: ThreadEditorValue;
  onChange: (next: ThreadEditorValue) => void;
  loading?: boolean;
  submitting?: boolean;
  editable?: boolean;
  error?: string;
  readOnlyHint?: string;
  postAtLabel?: string;
  itemsLabel?: string;
  emptyItemsLabel?: string;
  authorPlaceholder?: string;
  onClose: () => void;
  /** 저장 직전 postAt delta가 반영된 최종 value를 넘긴다. */
  onSave: (next: ThreadEditorValue) => void;
  /** Left-side destructive action (홀딩 취소 / 글 삭제 등) */
  destructiveAction?: ReactNode;
  testId?: string;
}

export function ThreadEditorDialog({
  open,
  title,
  value,
  onChange,
  loading = false,
  submitting = false,
  editable = true,
  error = '',
  readOnlyHint,
  postAtLabel = '글 발행 시각 (KST)',
  itemsLabel = '댓글 · 대댓글 일정',
  emptyItemsLabel = '댓글이 없습니다.',
  authorPlaceholder = 'authorId',
  onClose,
  onSave,
  destructiveAction,
  testId = 'admin-thread-editor-dialog',
}: ThreadEditorDialogProps) {
  /** 다이얼로그 로드 시점의 글 시각 — 저장 시 delta 기준. */
  const baselinePostAtRef = useRef('');
  const baselineCapturedRef = useRef(false);

  useEffect(() => {
    if (!open) {
      baselinePostAtRef.current = '';
      baselineCapturedRef.current = false;
      return;
    }
    if (!loading && !baselineCapturedRef.current && value.postAtLocal) {
      baselinePostAtRef.current = value.postAtLocal;
      baselineCapturedRef.current = true;
    }
  }, [open, loading, value.postAtLocal]);

  function patch(partial: Partial<ThreadEditorValue>) {
    onChange({ ...value, ...partial });
  }

  function updateItem(index: number, itemPatch: Partial<ThreadEditorItem>) {
    patch({
      items: value.items.map((it, i) => (i === index ? { ...it, ...itemPatch } : it)),
    });
  }

  function removeItem(index: number) {
    const target = value.items[index];
    patch({
      items: value.items.filter((it, i) => {
        if (i === index) return false;
        if (it.parentKey && target && it.parentKey === target.key) return false;
        return true;
      }),
    });
  }

  /** 저장 시 baseline→최종 postAt delta를 댓글에 일괄 적용 (키보드/피커 공통). */
  function handleSaveClick() {
    const baseline = baselinePostAtRef.current || value.postAtLocal;
    const next = applyPostAtDeltaToItems(value, baseline);
    if (next !== value) onChange(next);
    onSave(next);
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto" data-testid={testId}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>

        {loading ? (
          <p className="text-sm text-gray-500 py-8 text-center">불러오는 중…</p>
        ) : (
          <div className="space-y-4">
            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
                <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" />
                <p className="text-sm text-red-700">{error}</p>
              </div>
            )}

            {readOnlyHint && (
              <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-md p-3">
                {readOnlyHint}
              </p>
            )}

            <div className="space-y-2">
              <Label>제목</Label>
              <Input
                value={value.title}
                onChange={(e) => patch({ title: e.target.value })}
                disabled={!editable || submitting}
                data-testid="admin-thread-title"
              />
            </div>

            <div className="space-y-2">
              <Label>본문</Label>
              <Textarea
                value={value.body}
                onChange={(e) => patch({ body: e.target.value })}
                rows={6}
                disabled={!editable || submitting}
                data-testid="admin-thread-body"
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label>카테고리</Label>
                <Select
                  value={value.category}
                  onValueChange={(category) => patch({ category })}
                  disabled={!editable || submitting}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {THREAD_CATEGORY_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>{postAtLabel}</Label>
                <Input
                  type="datetime-local"
                  value={value.postAtLocal}
                  onChange={(e) => patch({ postAtLocal: e.target.value })}
                  disabled={!editable || submitting}
                  data-testid="admin-thread-post-at"
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label>{itemsLabel}</Label>
              {value.items.length === 0 ? (
                <p className="text-sm text-gray-500">{emptyItemsLabel}</p>
              ) : (
                <ul className="space-y-3" data-testid="admin-thread-items">
                  {value.items.map((it, index) => (
                    <li
                      key={it.key}
                      className="border rounded-md p-3 space-y-2 bg-gray-50/50"
                      data-testid={`admin-thread-item-${it.key}`}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2 text-xs text-gray-600">
                          <Badge variant={it.type === 'REPLY' ? 'secondary' : 'default'}>
                            {it.type === 'REPLY' ? '대댓글' : '댓글'}
                          </Badge>
                          <span className="font-mono">{it.authorId}</span>
                          <span className="text-gray-400">
                            {formatKstLabel(fromDatetimeLocalKst(it.atLocal))}
                          </span>
                          {it.status && it.status !== 'ACTIVE' && (
                            <Badge variant="outline">{it.status}</Badge>
                          )}
                        </div>
                        {editable && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removeItem(index)}
                            disabled={submitting}
                            aria-label="항목 삭제"
                          >
                            <Trash2 className="h-4 w-4 text-red-600" />
                          </Button>
                        )}
                      </div>
                      <Input
                        type="datetime-local"
                        value={it.atLocal}
                        onChange={(e) => updateItem(index, { atLocal: e.target.value })}
                        disabled={!editable || submitting}
                      />
                      <Input
                        value={it.authorId}
                        onChange={(e) => updateItem(index, { authorId: e.target.value })}
                        disabled={!editable || submitting}
                        placeholder={authorPlaceholder}
                      />
                      <Textarea
                        value={it.body}
                        onChange={(e) => updateItem(index, { body: e.target.value })}
                        rows={3}
                        disabled={!editable || submitting}
                      />
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}

        <DialogFooter className="gap-2 sm:gap-0">
          {editable && destructiveAction}
          <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
            닫기
          </Button>
          {editable && (
            <Button
              type="button"
              onClick={handleSaveClick}
              disabled={submitting || loading}
              data-testid="admin-thread-save"
            >
              {submitting ? '저장 중…' : '저장'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
