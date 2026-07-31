'use client';

import { useState } from 'react';
import { createPost, createComment, AdminPost, AdminComment } from '@/lib/api/admin/content';
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
  open: boolean;
  onClose: () => void;
  onCreated: (item: AdminPost | AdminComment) => void;
}

const CATEGORY_OPTIONS = [
  { value: 'COUPLE', label: '연인' },
  { value: 'MARRIED', label: '부부' },
  { value: 'FRIEND', label: '친구' },
  { value: 'FAMILY', label: '가족' },
  { value: 'WORK', label: '직장' },
  { value: 'OTHER', label: '기타' },
];

export function CreateContentDialog({ open, onClose, onCreated }: Props) {
  const [contentType, setContentType] = useState<'post' | 'comment' | 'reply'>('post');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [category, setCategory] = useState('OTHER');
  const [authorId, setAuthorId] = useState('');
  const [postId, setPostId] = useState('');
  const [parentCommentId, setParentCommentId] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  function resetForm() {
    setContentType('post');
    setTitle('');
    setBody('');
    setCategory('OTHER');
    setAuthorId('');
    setPostId('');
    setParentCommentId('');
    setError('');
  }

  function handleClose() {
    resetForm();
    onClose();
  }

  async function handleCreate() {
    setError('');
    setSubmitting(true);

    try {
      if (contentType === 'post') {
        if (!title.trim()) throw new Error('제목을 입력해주세요.');
        if (!body.trim()) throw new Error('본문을 입력해주세요.');
        if (!authorId.trim()) throw new Error('작성자를 입력해주세요.');
        if (!category) throw new Error('카테고리를 선택해주세요.');

        const created = await createPost({
          title: title.trim(),
          bodyRaw: body.trim(),
          category,
          authorId: authorId.trim(),
        });
        onCreated(created);
      } else if (contentType === 'comment') {
        if (!postId.trim()) throw new Error('게시글 ID를 입력해주세요.');
        if (!body.trim()) throw new Error('댓글 내용을 입력해주세요.');
        if (!authorId.trim()) throw new Error('작성자를 입력해주세요.');

        const created = await createComment({
          postId: postId.trim(),
          body: body.trim(),
          authorId: authorId.trim(),
        });
        onCreated(created);
      } else if (contentType === 'reply') {
        if (!postId.trim()) throw new Error('게시글 ID를 입력해주세요.');
        if (!parentCommentId.trim()) throw new Error('부모 댓글 ID를 입력해주세요.');
        if (!body.trim()) throw new Error('대댓글 내용을 입력해주세요.');
        if (!authorId.trim()) throw new Error('작성자를 입력해주세요.');

        const created = await createComment({
          postId: postId.trim(),
          parentCommentId: Number(parentCommentId),
          body: body.trim(),
          authorId: authorId.trim(),
        });
        onCreated(created);
      }

      handleClose();
    } catch (err: any) {
      setError(err?.response?.data?.message || err.message || '생성에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>콘텐츠 추가</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-md flex items-start gap-2">
              <AlertCircle className="w-5 h-5 text-red-600 mt-0.5 flex-shrink-0" />
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}

          {/* Content Type */}
          <div>
            <Label className="block text-sm font-medium mb-2">유형</Label>
            <Select value={contentType} onValueChange={(v: any) => setContentType(v)} disabled={submitting}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="post">게시글</SelectItem>
                <SelectItem value="comment">댓글</SelectItem>
                <SelectItem value="reply">대댓글</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Post Title — only for posts */}
          {contentType === 'post' && (
            <div>
              <Label className="block text-sm font-medium mb-2">제목</Label>
              <Input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="게시글 제목"
                disabled={submitting}
              />
            </div>
          )}

          {/* Post Category — only for posts */}
          {contentType === 'post' && (
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
          )}

          {/* Post ID — for comments and replies */}
          {(contentType === 'comment' || contentType === 'reply') && (
            <div>
              <Label className="block text-sm font-medium mb-2">게시글 ID</Label>
              <Input
                value={postId}
                onChange={(e) => setPostId(e.target.value)}
                placeholder="게시글 ID를 입력하세요"
                disabled={submitting}
              />
            </div>
          )}

          {/* Parent Comment ID — only for replies */}
          {contentType === 'reply' && (
            <div>
              <Label className="block text-sm font-medium mb-2">부모 댓글 ID</Label>
              <Input
                value={parentCommentId}
                onChange={(e) => setParentCommentId(e.target.value)}
                placeholder="부모 댓글 ID를 입력하세요"
                disabled={submitting}
              />
            </div>
          )}

          {/* Body */}
          <div>
            <Label className="block text-sm font-medium mb-2">
              {contentType === 'post' ? '본문' : '내용'}
            </Label>
            <Textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder={contentType === 'post' ? '게시글 본문을 입력하세요' : '내용을 입력하세요'}
              disabled={submitting}
              rows={8}
              className="resize-none"
            />
          </div>

          {/* Author ID */}
          <div>
            <Label className="block text-sm font-medium mb-2">작성자</Label>
            <Input
              value={authorId}
              onChange={(e) => setAuthorId(e.target.value)}
              placeholder="작성자 이름 (존재 여부와 무관하게 자유 입력)"
              disabled={submitting}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={submitting}>
            취소
          </Button>
          <Button onClick={handleCreate} disabled={submitting}>
            {submitting ? '생성 중...' : '생성'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
