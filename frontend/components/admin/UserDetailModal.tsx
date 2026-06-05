'use client';

import { useEffect, useState } from 'react';
import { getAdminUserDetail, deleteUserData, type AdminUserDetail } from '@/lib/api/admin';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { AlertCircle } from 'lucide-react';

interface Props {
  userId: string | null;
  onClose: () => void;
  onAnonymized?: (id: string) => void;
}

export function UserDetailModal({ userId, onClose, onAnonymized }: Props) {
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [anonymizing, setAnonymizing] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!userId) return;
    setLoading(true);
    setError('');
    setDetail(null);
    getAdminUserDetail(userId)
      .then(setDetail)
      .catch(() => setError('사용자 정보를 불러오지 못했어요.'))
      .finally(() => setLoading(false));
  }, [userId]);

  if (!userId) return null;

  async function handleAnonymize() {
    if (!detail) return;
    if (
      !confirm(
        `${detail.nickname} (${detail.id}) 사용자의 데이터를 익명화하시겠어요?\n\n이 작업은 되돌릴 수 없으며, 사용자의 모든 식별 정보가 즉시 삭제 예약됩니다.`,
      )
    )
      return;
    setAnonymizing(true);
    try {
      await deleteUserData(detail.id);
      onAnonymized?.(detail.id);
      onClose();
    } catch {
      setError('익명화 요청에 실패했어요.');
      setAnonymizing(false);
    }
  }

  return (
    <Dialog open={!!userId} onOpenChange={onClose}>
      <DialogContent className="max-w-xl max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            사용자 상세
            {detail?.deletedAt && (
              <span className="text-sm text-red-600 font-normal">(탈퇴)</span>
            )}
          </DialogTitle>
        </DialogHeader>

        <div className="py-4">
          {loading && (
            <div className="text-center py-8 text-gray-500">불러오는 중…</div>
          )}
          {error && (
            <div className="flex items-center gap-2 p-4 bg-red-50 border border-red-200 rounded text-red-700 text-sm">
              <AlertCircle size={16} className="flex-shrink-0" />
              {error}
            </div>
          )}
          {detail && (
            <div className="space-y-6">
              <Section title="기본 정보">
                <Meta label="ID" value={detail.id} mono />
                <Meta label="닉네임" value={detail.nickname} />
                <Meta label="이메일" value={detail.email || '-'} />
                <Meta label="등급" value={detail.isGuest ? '게스트' : '회원'} />
                <Meta label="역할" value={detail.roles?.join(', ') || 'USER'} />
                <Meta label="가입 경로" value={detail.provider || '이메일'} />
                <Meta label="가입일" value={fmt(detail.createdAt)} />
                {detail.deletedAt && (
                  <Meta label="탈퇴일" value={fmt(detail.deletedAt)} highlight />
                )}
              </Section>

              <Section title="프로필">
                <Meta label="MBTI" value={detail.mbtiType || '-'} />
                <Meta label="통신 스타일" value={detail.communicationStyle || '-'} />
                <Meta label="온보딩 완료" value={fmt(detail.onboardingCompletedAt) || '미완료'} />
              </Section>

              <Section title="동의 상태">
                <Meta label="이용약관" value={fmt(detail.termsAgreedAt) || '미동의'} />
                <Meta label="개인정보처리방침" value={fmt(detail.privacyAgreedAt) || '미동의'} />
                <Meta label="전문상담 비대체" value={fmt(detail.disclaimerAgreedAt) || '미동의'} />
                <Meta label="마케팅 수신" value={fmt(detail.marketingAgreedAt) || '미동의'} />
              </Section>

              <Section title="활동 통계">
                <Meta label="총 세션" value={`${detail.totalSessions}건`} />
                <Meta label="완료 세션" value={`${detail.completedSessions}건`} />
                <Meta label="의견 제출" value={`${detail.feedbackCount}건`} />
                <Meta label="마지막 세션" value={fmt(detail.lastSessionAt) || '없음'} />
              </Section>
            </div>
          )}
        </div>

        {detail && !detail.deletedAt && (
          <DialogFooter>
            <Button
              variant="destructive"
              onClick={handleAnonymize}
              disabled={anonymizing}
              className="w-full"
            >
              {anonymizing ? '처리 중...' : '데이터 익명화 (되돌릴 수 없음)'}
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h3 className="text-xs font-semibold text-gray-600 uppercase tracking-wide mb-3">
        {title}
      </h3>
      <div className="space-y-2">{children}</div>
    </div>
  );
}

function Meta({
  label,
  value,
  mono,
  highlight,
}: {
  label: string;
  value: string;
  mono?: boolean;
  highlight?: boolean;
}) {
  return (
    <div className="flex text-sm gap-3">
      <span className="text-gray-600 min-w-[100px] flex-shrink-0">{label}</span>
      <span
        className={`flex-1 ${
          highlight ? 'text-red-600' : 'text-gray-900'
        } break-all ${mono ? 'font-mono text-xs' : ''}`}
      >
        {value}
      </span>
    </div>
  );
}

function fmt(iso?: string | null): string {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleString('ko-KR', {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  } catch {
    return iso;
  }
}
