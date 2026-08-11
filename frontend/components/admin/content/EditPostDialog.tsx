'use client';

import { useState, useEffect } from 'react';
import { updatePost } from '@/lib/api/admin/content';
import { AdminPost } from '@/lib/api/admin/content';
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
import { AlertCircle } from 'lucide-react';

interface Props {
  post: AdminPost | null;
  onClose: () => void;
  onUpdated: (updated: AdminPost) => void;
}

// CLOSED(시한부 투표 종료)는 레거시 — 신규 선택 금지. 기존 CLOSED 글만 표시용으로 노출.
const POST_STATUS_OPTIONS = [
  { value: 'DRAFT', label: '초안' },
  { value: 'VOTING', label: '공개' },
  { value: 'BLOCKED', label: '차단됨' },
];

const CLOSED_LEGACY_OPTION = { value: 'CLOSED', label: '종료(레거시)' };

const CATEGORY_OPTIONS = [
  { value: 'COUPLE', label: '연인' },
  { value: 'MARRIED', label: '부부' },
  { value: 'FRIEND', label: '친구' },
  { value: 'FAMILY', label: '가족' },
  { value: 'WORK', label: '직장' },
  { value: 'OTHER', label: '기타' },
];

export function EditPostDialog({ post, onClose, onUpdated }: Props) {
  const [title, setTitle] = useState('');
  const [bodyRaw, setBodyRaw] = useState('');
  const [partnerBodyRaw, setPartnerBodyRaw] = useState('');
  const [status, setStatus] = useState('VOTING');
  const [category, setCategory] = useState('OTHER');
  const [viewCount, setViewCount] = useState<number | ''>('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!post) return;
    setTitle(post.title || '');
    setBodyRaw(post.bodyRaw || post.bodyPublished || '');
    setPartnerBodyRaw(post.partnerBodyRaw || post.partnerBodyPublished || '');
    setStatus(post.status || 'VOTING');
    setCategory(post.category || 'OTHER');
    setViewCount(post.viewCount ?? '');
    setError('');
  }, [post]);

  if (!post) return null;

  async function handleSave() {
    if (!post) return;
    setSubmitting(true);
    setError('');
    try {
      const payload: Parameters<typeof updatePost>[1] = { title, bodyRaw, status, category };
      if (partnerBodyRaw) payload.partnerBodyRaw = partnerBodyRaw;
      if (viewCount !== '') payload.viewCount = Number(viewCount);
      const updated = await updatePost(post.id, payload);
      onUpdated(updated);
      onClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || '저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={!!post} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>게시글 수정</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
              <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" />
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          {/* Title */}
          <div>
            <Label className="block text-sm font-medium mb-2">제목</Label>
            <Input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="게시글 제목"
              disabled={submitting}
            />
          </div>

          {/* Category */}
          <div>
            <Label className="block text-sm font-medium mb-2">카테고리</Label>
            <Select value={category} onValueChange={setCategory} disabled={submitting}>
              <SelectTrigger>
                <SelectValue placeholder="카테고리 선택" />
              </SelectTrigger>
              <SelectContent>
                {CATEGORY_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Status */}
          <div>
            <Label className="block text-sm font-medium mb-2">상태</Label>
            <Select value={status} onValueChange={setStatus} disabled={submitting}>
              <SelectTrigger>
                <SelectValue placeholder="상태 선택" />
              </SelectTrigger>
              <SelectContent>
                {(status === 'CLOSED'
                  ? [...POST_STATUS_OPTIONS, CLOSED_LEGACY_OPTION]
                  : POST_STATUS_OPTIONS
                ).map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* View Count */}
          <div>
            <Label className="block text-sm font-medium mb-2">조회수</Label>
            <Input
              type="number"
              value={viewCount}
              onChange={(e) => setViewCount(e.target.value === '' ? '' : Number(e.target.value))}
              placeholder="조회수 입력 (선택사항)"
              disabled={submitting}
              min="0"
            />
          </div>

          {/* 작성자 본문 */}
          <div>
            <Label className="block text-sm font-medium mb-2">작성자 본문</Label>
            <Textarea
              value={bodyRaw}
              onChange={(e) => setBodyRaw(e.target.value)}
              placeholder="작성자 본문"
              disabled={submitting}
              rows={8}
              className="resize-none"
            />
          </div>

          {/* 상대방 본문 — 상대방 답변이 있을 때만 표시 */}
          {(post.partnerBodyPublished || post.partnerBodyRaw) && (
            <div>
              <Label className="block text-sm font-medium mb-2">상대방 본문</Label>
              <Textarea
                value={partnerBodyRaw}
                onChange={(e) => setPartnerBodyRaw(e.target.value)}
                placeholder="상대방 본문"
                disabled={submitting}
                rows={8}
                className="resize-none"
              />
            </div>
          )}
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
