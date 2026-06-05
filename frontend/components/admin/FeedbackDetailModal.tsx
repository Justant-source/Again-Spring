'use client';

import { useState, useEffect } from 'react';
import { updateFeedbackStatus } from '@/lib/api/admin';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Label } from '@/components/ui/label';
import { AlertCircle, Copy, Mail } from 'lucide-react';

export interface AdminFeedback {
  id: number;
  userId?: string;
  category: string;
  content: string;
  status: string;
  adminNote?: string;
  contactConsent?: boolean;
  contactEmail?: string;
  pageUrl?: string;
  createdAt?: string;
}

interface Props {
  feedback: AdminFeedback | null;
  onClose: () => void;
  onUpdated: (updated: AdminFeedback) => void;
}

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: 'pending', label: '대기' },
  { value: 'reviewed', label: '검토 완료' },
  { value: 'resolved', label: '해결됨' },
];

export const CATEGORY_BADGE: Record<
  string,
  { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }
> = {
  ui_bug: { label: 'UI 버그', variant: 'destructive' },
  feature: { label: '기능 제안', variant: 'default' },
  content: { label: '내용/카피', variant: 'secondary' },
  crisis: { label: '위기', variant: 'destructive' },
  praise: { label: '칭찬', variant: 'secondary' },
  other: { label: '기타', variant: 'outline' },
};

export function FeedbackDetailModal({ feedback, onClose, onUpdated }: Props) {
  const [status, setStatus] = useState('pending');
  const [adminNote, setAdminNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!feedback) return;
    setStatus(feedback.status || 'pending');
    setAdminNote(feedback.adminNote || '');
    setError('');
  }, [feedback]);

  if (!feedback) return null;

  const badge = CATEGORY_BADGE[feedback.category] || CATEGORY_BADGE.other;

  async function handleSave() {
    if (!feedback) return;
    setSubmitting(true);
    setError('');
    try {
      const updated = await updateFeedbackStatus(feedback.id, status, adminNote);
      onUpdated(updated);
      onClose();
    } catch {
      setError('저장에 실패했어요. 잠시 후 다시 시도해주세요.');
    } finally {
      setSubmitting(false);
    }
  }

  const handleCopyEmail = () => {
    if (feedback.contactEmail) {
      navigator.clipboard?.writeText(feedback.contactEmail);
    }
  };

  return (
    <Dialog open={!!feedback} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-3">
            <Badge variant={badge.variant}>{badge.label}</Badge>
            <span className="text-xs text-gray-500 font-normal">#{feedback.id}</span>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* Meta info */}
          <div className="space-y-2 text-sm">
            <Meta
              label="사용자 ID"
              value={feedback.userId || '익명'}
            />
            <Meta
              label="작성 일시"
              value={
                feedback.createdAt
                  ? new Date(feedback.createdAt).toLocaleString('ko-KR')
                  : '-'
              }
            />
          </div>

          {/* Contact email section */}
          {feedback.contactConsent && feedback.contactEmail ? (
            <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg">
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1">
                  <div className="flex items-center gap-2 text-xs font-semibold text-amber-900 mb-1">
                    <Mail size={14} />
                    회신 동의 — 답변 회신 요청
                  </div>
                  <a
                    href={`mailto:${feedback.contactEmail}?subject=${encodeURIComponent(
                      `[다시봄] 의견 #${feedback.id} 답변`,
                    )}`}
                    className="text-sm text-blue-600 break-all hover:underline font-mono"
                  >
                    {feedback.contactEmail}
                  </a>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleCopyEmail}
                  className="flex-shrink-0"
                >
                  <Copy size={14} />
                </Button>
              </div>
            </div>
          ) : feedback.contactConsent ? (
            <Meta label="회신 동의" value="동의 (이메일 미입력)" />
          ) : (
            <Meta label="회신 동의" value="비동의" />
          )}

          {feedback.pageUrl && (
            <Meta label="작성 페이지" value={feedback.pageUrl} />
          )}

          {/* Content */}
          <div>
            <Label className="text-xs font-semibold mb-2 block">의견 본문</Label>
            <div className="p-3 bg-gray-50 rounded border whitespace-pre-wrap break-words text-sm text-gray-900 max-h-32 overflow-y-auto">
              {feedback.content}
            </div>
          </div>

          {/* Status */}
          <div>
            <Label className="text-xs font-semibold mb-3 block">처리 상태</Label>
            <RadioGroup value={status} onValueChange={setStatus}>
              <div className="flex flex-wrap gap-3">
                {STATUS_OPTIONS.map((opt) => (
                  <div key={opt.value} className="flex items-center space-x-2">
                    <RadioGroupItem value={opt.value} id={`status-${opt.value}`} />
                    <Label
                      htmlFor={`status-${opt.value}`}
                      className="cursor-pointer font-normal"
                    >
                      {opt.label}
                    </Label>
                  </div>
                ))}
              </div>
            </RadioGroup>
          </div>

          {/* Admin note */}
          <div>
            <Label htmlFor="admin-note" className="text-xs font-semibold mb-2 block">
              관리자 메모
            </Label>
            <Textarea
              id="admin-note"
              value={adminNote}
              onChange={(e) => setAdminNote(e.target.value)}
              placeholder="처리 내용·후속 조치 등을 메모로 남겨주세요"
              rows={4}
            />
          </div>

          {error && (
            <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
              <AlertCircle size={16} className="flex-shrink-0" />
              {error}
            </div>
          )}
        </div>

        <DialogFooter className="flex gap-2">
          <Button
            variant="outline"
            onClick={onClose}
            disabled={submitting}
            className="flex-1"
          >
            취소
          </Button>
          <Button
            onClick={handleSave}
            disabled={submitting}
            className="flex-[2]"
          >
            {submitting ? '저장 중...' : '저장'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-3 text-sm">
      <span className="text-gray-600 min-w-[80px] flex-shrink-0">{label}</span>
      <span className="text-gray-900 flex-1">{value}</span>
    </div>
  );
}
