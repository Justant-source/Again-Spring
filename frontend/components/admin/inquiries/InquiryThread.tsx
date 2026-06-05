'use client';

import { useState, useEffect } from 'react';
import { replyToInquiry, closeInquiry, getInquiryDetail } from '@/lib/api/admin/inquiries';
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
import { AlertCircle, Send } from 'lucide-react';
import type { InquiryDetailResponse } from '@/lib/api/admin/inquiries';

interface Props {
  inquiryId: string | null;
  onClose: () => void;
  onUpdated?: () => void;
}

const STATUS_BADGE: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  OPEN: { label: '열림', variant: 'default' },
  ANSWERED: { label: '답변됨', variant: 'secondary' },
  CLOSED: { label: '종료', variant: 'outline' },
};

const CATEGORY_BADGE: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  '기술지원': { label: '기술지원', variant: 'default' },
  '결제': { label: '결제', variant: 'secondary' },
  '계정': { label: '계정', variant: 'secondary' },
  '기타': { label: '기타', variant: 'outline' },
};

export function InquiryThread({ inquiryId, onClose, onUpdated }: Props) {
  const [detail, setDetail] = useState<InquiryDetailResponse | null>(null);
  const [replyMessage, setReplyMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [closing, setClosing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!inquiryId) return;

    const loadDetail = async () => {
      setLoading(true);
      setError('');
      try {
        const data = await getInquiryDetail(inquiryId);
        setDetail(data);
      } catch (err) {
        setError('문의를 불러올 수 없어요. 잠시 후 다시 시도해주세요.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };

    loadDetail();
  }, [inquiryId]);

  if (!inquiryId) return null;

  const statusBadge = detail ? STATUS_BADGE[detail.status] || STATUS_BADGE.OPEN : null;
  const categoryBadge = detail ? CATEGORY_BADGE[detail.category] || CATEGORY_BADGE['기타'] : null;

  const handleSendReply = async () => {
    if (!detail || !replyMessage.trim()) return;

    setSubmitting(true);
    setError('');
    try {
      const updated = await replyToInquiry(detail.id, replyMessage);
      setDetail(updated);
      setReplyMessage('');
      onUpdated?.();
    } catch (err) {
      setError('답변 전송에 실패했어요. 잠시 후 다시 시도해주세요.');
      console.error(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleClose = async () => {
    if (!detail) return;

    setClosing(true);
    setError('');
    try {
      await closeInquiry(detail.id);
      setDetail({ ...detail, status: 'CLOSED' });
      onUpdated?.();
    } catch (err) {
      setError('문의 종료에 실패했어요. 잠시 후 다시 시도해주세요.');
      console.error(err);
    } finally {
      setClosing(false);
    }
  };

  return (
    <Dialog open={!!inquiryId} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[85vh] flex flex-col">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <div className="text-sm text-gray-500">로드 중...</div>
          </div>
        ) : detail ? (
          <>
            <DialogHeader>
              <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex-1">
                  <DialogTitle className="text-base">{detail.subject}</DialogTitle>
                  <div className="flex items-center gap-2 mt-2 flex-wrap">
                    <Badge variant={categoryBadge?.variant || 'outline'}>
                      {categoryBadge?.label || detail.category}
                    </Badge>
                    <Badge variant={statusBadge?.variant || 'default'}>
                      {statusBadge?.label || detail.status}
                    </Badge>
                    <span className="text-xs text-gray-500">#{detail.id}</span>
                  </div>
                </div>
              </div>
              <div className="text-xs text-gray-600 space-y-1 mt-3 pt-3 border-t">
                <div>사용자: <span className="font-mono">{detail.userId}</span></div>
                <div>등록: {new Date(detail.createdAt).toLocaleString('ko-KR')}</div>
              </div>
            </DialogHeader>

            {/* Messages */}
            <div className="flex-1 overflow-y-auto border-t border-b py-4 px-4 space-y-4 bg-gray-50">
              {detail.messages && detail.messages.length > 0 ? (
                detail.messages.map((msg, idx) => (
                  <div
                    key={idx}
                    className={`flex ${msg.senderRole === 'ADMIN' ? 'justify-end' : 'justify-start'}`}
                  >
                    <div
                      className={`max-w-xs lg:max-w-md xl:max-w-lg rounded-lg p-3 ${
                        msg.senderRole === 'ADMIN'
                          ? 'bg-blue-100 text-blue-900'
                          : 'bg-white text-gray-900 border border-gray-200'
                      }`}
                    >
                      <div className="text-xs font-semibold mb-1">
                        {msg.senderRole === 'ADMIN' ? '관리자' : '사용자'}
                      </div>
                      <p className="text-sm whitespace-pre-wrap break-words">{msg.body}</p>
                      <div className="text-xs text-gray-500 mt-2">
                        {new Date(msg.createdAt).toLocaleString('ko-KR')}
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center text-gray-500 text-sm py-4">메시지가 없습니다.</div>
              )}
            </div>

            {/* Reply input */}
            <div className="space-y-3 py-4">
              {error && (
                <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
                  <AlertCircle size={16} className="flex-shrink-0" />
                  {error}
                </div>
              )}

              {detail.status !== 'CLOSED' && (
                <div>
                  <label className="text-xs font-semibold mb-2 block">답변 메시지</label>
                  <Textarea
                    value={replyMessage}
                    onChange={(e) => setReplyMessage(e.target.value)}
                    placeholder="답변을 입력하세요"
                    rows={3}
                    disabled={submitting}
                  />
                </div>
              )}
            </div>

            <DialogFooter className="flex gap-2">
              <Button
                variant="outline"
                onClick={onClose}
                disabled={submitting || closing}
                className="flex-1"
              >
                닫기
              </Button>

              {detail.status !== 'CLOSED' && (
                <>
                  {detail.status === 'ANSWERED' && (
                    <Button
                      variant="secondary"
                      onClick={handleClose}
                      disabled={submitting || closing}
                    >
                      {closing ? '종료 중...' : '문의 종료'}
                    </Button>
                  )}

                  <Button
                    onClick={handleSendReply}
                    disabled={!replyMessage.trim() || submitting || closing}
                    className="flex-1"
                  >
                    <Send size={16} className="mr-2" />
                    {submitting ? '전송 중...' : '답변 전송'}
                  </Button>
                </>
              )}
            </DialogFooter>
          </>
        ) : error ? (
          <div className="flex items-center justify-center py-8">
            <div className="text-sm text-red-500">{error}</div>
          </div>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
