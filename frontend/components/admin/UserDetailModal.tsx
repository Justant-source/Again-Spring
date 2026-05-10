'use client';

import { useEffect, useState } from 'react';
import { getAdminUserDetail, deleteUserData, type AdminUserDetail } from '@/lib/api/admin';

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
    if (!confirm(`${detail.nickname} (${detail.id}) 사용자의 데이터를 익명화하시겠어요?\n\n이 작업은 되돌릴 수 없으며, 사용자의 모든 식별 정보가 즉시 삭제 예약됩니다.`)) return;
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
    <div
      role="dialog"
      aria-modal="true"
      style={{
        position: 'fixed', inset: 0,
        background: 'rgba(0,0,0,0.5)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        zIndex: 10000, padding: 16,
      }}
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          background: 'white', borderRadius: 12,
          maxWidth: 560, width: '100%',
          maxHeight: '85vh',
          display: 'flex', flexDirection: 'column',
          boxShadow: '0 4px 24px rgba(0,0,0,0.2)',
        }}
      >
        <div style={{ padding: '16px 20px', borderBottom: '1px solid #eee', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ fontSize: 15, fontWeight: 600, color: '#111' }}>
            사용자 상세 {detail?.deletedAt && <span style={{ color: '#e55', fontSize: 12, marginLeft: 8 }}>(탈퇴)</span>}
          </div>
          <button
            onClick={onClose}
            aria-label="닫기"
            style={{ background: 'none', border: 'none', fontSize: 20, color: '#888', cursor: 'pointer', padding: 4 }}
          >
            ×
          </button>
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: '16px 20px' }}>
          {loading && <div style={{ padding: 20, textAlign: 'center', color: '#888' }}>불러오는 중…</div>}
          {error && <div style={{ padding: 20, textAlign: 'center', color: '#e55' }}>{error}</div>}
          {detail && (
            <>
              <Section title="기본 정보">
                <Meta label="ID" value={detail.id} mono />
                <Meta label="닉네임" value={detail.nickname} />
                <Meta label="이메일" value={detail.email || '-'} />
                <Meta label="등급" value={detail.isGuest ? '게스트' : '회원'} />
                <Meta label="역할" value={detail.roles?.join(', ') || 'USER'} />
                <Meta label="가입 경로" value={detail.provider || '이메일'} />
                <Meta label="가입일" value={fmt(detail.createdAt)} />
                {detail.deletedAt && <Meta label="탈퇴일" value={fmt(detail.deletedAt)} highlight />}
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
            </>
          )}
        </div>

        {detail && !detail.deletedAt && (
          <div style={{ padding: '14px 20px', borderTop: '1px solid #eee', background: '#fafafa' }}>
            <button
              onClick={handleAnonymize}
              disabled={anonymizing}
              style={{
                width: '100%', padding: 11, borderRadius: 8,
                background: '#e55', color: 'white', border: 'none',
                fontSize: 13, fontWeight: 600,
                cursor: anonymizing ? 'not-allowed' : 'pointer',
                opacity: anonymizing ? 0.6 : 1,
              }}
            >
              {anonymizing ? '처리 중...' : '데이터 익명화 (되돌릴 수 없음)'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div style={{ fontSize: 12, color: '#888', fontWeight: 600, marginBottom: 8 }}>{title}</div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>{children}</div>
    </div>
  );
}

function Meta({ label, value, mono, highlight }: { label: string; value: string; mono?: boolean; highlight?: boolean }) {
  return (
    <div style={{ display: 'flex', fontSize: 12, gap: 12 }}>
      <span style={{ color: '#888', minWidth: 110 }}>{label}</span>
      <span
        style={{
          color: highlight ? '#e55' : '#333',
          fontFamily: mono ? 'ui-monospace, monospace' : 'inherit',
          fontSize: mono ? 11 : 12,
          overflowWrap: 'anywhere',
        }}
      >
        {value}
      </span>
    </div>
  );
}

function fmt(iso?: string | null): string {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleString('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });
  } catch {
    return iso;
  }
}
