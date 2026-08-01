'use client';

import { useEffect, useState } from 'react';
import {
  getPublishedThread,
  updatePublishedThread,
  deletePost,
} from '@/lib/api/admin/content';
import { Button } from '@/components/ui/button';
import { ThreadEditorDialog } from './thread-editor/ThreadEditorDialog';
import { fromDatetimeLocalKst, toDatetimeLocalKst } from './thread-editor/datetimeKst';
import type { ThreadEditorValue } from './thread-editor/types';

interface Props {
  postId: string | null;
  onClose: () => void;
  onSaved: () => void;
  onDeleted: () => void;
}

const EMPTY: ThreadEditorValue = {
  title: '',
  body: '',
  category: 'OTHER',
  postAtLocal: '',
  items: [],
};

export function EditPublishedThreadDialog({ postId, onClose, onSaved, onDeleted }: Props) {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [value, setValue] = useState<ThreadEditorValue>(EMPTY);

  useEffect(() => {
    if (!postId) {
      setValue(EMPTY);
      return;
    }
    setLoading(true);
    setError('');
    getPublishedThread(postId)
      .then((d) => {
        setValue({
          title: d.title || '',
          body: d.body || '',
          category: d.category || 'OTHER',
          postAtLocal: toDatetimeLocalKst(d.createdAt),
          items: (d.items || []).map((it) => ({
            key: String(it.id),
            parentKey: it.parentCommentId != null ? String(it.parentCommentId) : null,
            authorId: it.authorId || '',
            body: it.body || '',
            type: it.type,
            atLocal: toDatetimeLocalKst(it.createdAt),
            status: it.status || undefined,
          })),
        });
      })
      .catch((err: any) => {
        setError(err?.response?.data?.message || '공개 글을 불러오지 못했어요.');
      })
      .finally(() => setLoading(false));
  }, [postId]);

  if (!postId) return null;

  async function handleSave(next: ThreadEditorValue) {
    if (!postId) return;
    setSubmitting(true);
    setError('');
    try {
      await updatePublishedThread(postId, {
        title: next.title,
        body: next.body,
        category: next.category,
        createdAt: fromDatetimeLocalKst(next.postAtLocal),
        items: next.items.map((it) => ({
          id: Number(it.key),
          body: it.body,
          authorId: it.authorId,
          createdAt: fromDatetimeLocalKst(it.atLocal),
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

  async function handleDelete() {
    if (!postId) return;
    if (!window.confirm('이 공개 글을 삭제할까요? (소프트 삭제)')) return;
    setSubmitting(true);
    setError('');
    try {
      await deletePost(postId);
      onDeleted();
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '삭제에 실패했어요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <ThreadEditorDialog
      open={!!postId}
      title="공개 글 · 댓글 수정"
      value={value}
      onChange={setValue}
      loading={loading}
      submitting={submitting}
      editable
      error={error}
      postAtLabel="글 작성 시각 (KST)"
      itemsLabel="댓글 · 대댓글 타임라인"
      emptyItemsLabel="댓글이 없습니다."
      authorPlaceholder="authorId"
      onClose={onClose}
      onSave={handleSave}
      testId="admin-published-edit-dialog"
      destructiveAction={
        <Button
          type="button"
          variant="destructive"
          onClick={handleDelete}
          disabled={submitting || loading}
          data-testid="admin-published-delete"
        >
          글 삭제
        </Button>
      }
    />
  );
}
