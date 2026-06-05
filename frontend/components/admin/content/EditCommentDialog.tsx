'use client';

import { useState, useEffect } from 'react';
import { updateComment } from '@/lib/api/admin/content';
import { AdminComment } from '@/lib/api/admin/content';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { AlertCircle } from 'lucide-react';

interface Props {
  comment: AdminComment | null;
  onClose: () => void;
  onUpdated: (updated: AdminComment) => void;
}

export function EditCommentDialog({ comment, onClose, onUpdated }: Props) {
  const [body, setBody] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!comment) return;
    setBody(comment.body || '');
    setError('');
  }, [comment]);

  if (!comment) return null;

  async function handleSave() {
    if (!comment) return;
    setSubmitting(true);
    setError('');
    try {
      const updated = await updateComment(comment.id, { body });
      onUpdated(updated);
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={!!comment} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>댓글 수정</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
              <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" />
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          <div>
            <Label className="block text-sm font-medium mb-2">내용</Label>
            <Textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="댓글 내용"
              disabled={submitting}
              rows={6}
              className="resize-none"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            취소
          </Button>
          <Button onClick={handleSave} disabled={submitting}>
            {submitting ? '저장 중...' : '저장'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
