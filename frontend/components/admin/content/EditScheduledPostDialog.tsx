'use client';

import { useEffect, useState } from 'react';
import {
  getScheduledHolding,
  updateScheduledHolding,
  cancelScheduledHolding,
} from '@/lib/api/admin/content';
import { Button } from '@/components/ui/button';
import { ThreadEditorDialog } from './thread-editor/ThreadEditorDialog';
import { fromDatetimeLocalKst, toDatetimeLocalKst } from './thread-editor/datetimeKst';
import type { ThreadEditorValue } from './thread-editor/types';

interface Props {
  holdingId: string | null;
  onClose: () => void;
  onSaved: () => void;
  onCancelled: () => void;
}

const EMPTY: ThreadEditorValue = {
  title: '',
  body: '',
  category: 'OTHER',
  postAtLocal: '',
  items: [],
};

export function EditScheduledPostDialog({ holdingId, onClose, onSaved, onCancelled }: Props) {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [value, setValue] = useState<ThreadEditorValue>(EMPTY);

  useEffect(() => {
    if (!holdingId) {
      setStatus(null);
      setValue(EMPTY);
      return;
    }
    setLoading(true);
    setError('');
    getScheduledHolding(holdingId)
      .then((d) => {
        setStatus(d.status);
        setValue({
          title: d.title || '',
          body: d.body || '',
          category: d.category || 'OTHER',
          postAtLocal: toDatetimeLocalKst(d.scheduledPublishAt),
          items: (d.items || []).map((it) => ({
            key: it.ref,
            parentKey: it.parentRef || null,
            authorId: it.personaId,
            body: it.body,
            type: it.type,
            atLocal: toDatetimeLocalKst(it.scheduledAt),
          })),
        });
      })
      .catch((err: any) => {
        setError(err?.response?.data?.message || '홀딩 글을 불러오지 못했어요.');
      })
      .finally(() => setLoading(false));
  }, [holdingId]);

  if (!holdingId) return null;

  const editable = status === 'SCHEDULED';

  async function handleSave(next: ThreadEditorValue) {
    if (!holdingId || !editable) return;
    setSubmitting(true);
    setError('');
    try {
      await updateScheduledHolding(holdingId, {
        title: next.title,
        body: next.body,
        category: next.category,
        scheduledPublishAt: fromDatetimeLocalKst(next.postAtLocal),
        items: next.items.map((it) => ({
          ref: it.key,
          parentRef: it.parentKey || null,
          personaId: it.authorId,
          body: it.body,
          scheduledAt: fromDatetimeLocalKst(it.atLocal),
        })),
      });
      onSaved();
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancel() {
    if (!holdingId || !editable) return;
    if (!window.confirm('이 예약 홀딩을 취소할까요? 발행되지 않습니다.')) return;
    setSubmitting(true);
    setError('');
    try {
      await cancelScheduledHolding(holdingId);
      onCancelled();
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '취소에 실패했어요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ThreadEditorDialog
      open={!!holdingId}
      title="예약 홀딩 수정"
      value={value}
      onChange={setValue}
      loading={loading}
      submitting={submitting}
      editable={editable}
      error={error}
      readOnlyHint={
        !editable && status
          ? `상태가 ${status}이라 수정할 수 없습니다. 조회만 가능합니다.`
          : undefined
      }
      postAtLabel="글 발행 예정 (KST)"
      itemsLabel="댓글 · 대댓글 릴리스 일정"
      emptyItemsLabel="후보가 없습니다."
      authorPlaceholder="personaId"
      onClose={onClose}
      onSave={handleSave}
      testId="admin-scheduled-edit-dialog"
      destructiveAction={
        <Button
          type="button"
          variant="destructive"
          onClick={handleCancel}
          disabled={submitting || loading}
          data-testid="admin-scheduled-cancel"
        >
          홀딩 취소
        </Button>
      }
    />
  );
}
