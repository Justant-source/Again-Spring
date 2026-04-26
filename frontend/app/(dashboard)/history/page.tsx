// ⚠️ MOCKUP PENDING — design/mockups/11-history/ not yet provided; baseline Tone L layout used

'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore } from '@/lib/store/userStore';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { api } from '@/lib/api/client';
import type { RelationType } from '@/lib/types';

interface HistoryItem {
  id: string;
  partnerNickname: string;
  relationType: RelationType;
  conflictType: string | null;
  completedAt: string;
  summary?: string;
}

const RELATION_TYPE_LABEL: Record<RelationType, string> = {
  couple: '연인',
  marriage: '부부',
  friend: '친구',
  family: '가족',
  parent_child: '부모·자식',
  korean_specific: '한국 특화',
};

export default function HistoryPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) {
      router.push('/login');
      return;
    }

    if (user.isGuest) {
      // Guest user - show message but don't fetch
      setLoading(false);
      return;
    }

    // Fetch history for registered user
    const fetchHistory = async () => {
      try {
        const res = await api.get('/api/users/me/history');
        setHistory(res.data || []);
      } catch (err) {
        console.error('Failed to fetch history:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchHistory();
  }, [user, router]);

  if (!user) {
    return null;
  }

  if (loading) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="지나온 이야기" tone="L" onBack={() => router.back()} />
        <div style={{ padding: '28px', textAlign: 'center', color: 'var(--L-sub)' }}>
          로딩 중...
        </div>
      </PhoneFrame>
    );
  }

  // Guest user message
  if (user.isGuest) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="지나온 이야기" tone="L" onBack={() => router.back()} />
        <div style={{ padding: '28px 28px 40px', display: 'flex', flexDirection: 'column', gap: 24 }}>
          <div style={{ textAlign: 'center', marginTop: 40 }}>
            <div className="serif" style={{ fontSize: 20, lineHeight: 1.5, marginBottom: 16 }}>
              게스트 모드에서는<br />이력이 저장되지 않아요.
            </div>
            <div style={{ fontSize: 14, color: 'var(--L-sub)', lineHeight: 1.6 }}>
              회원가입 후 모든 대화를<br />저장하고 언제든 다시 볼 수 있어요.
            </div>
          </div>

          <button
            onClick={() => router.push('/signup')}
            className="btn-L"
            style={{ width: '100%', marginTop: 24 }}
          >
            회원가입 하기
          </button>
        </div>
      </PhoneFrame>
    );
  }

  // Empty state
  if (history.length === 0) {
    return (
      <PhoneFrame tone="L">
        <PhoneHeader title="지나온 이야기" tone="L" onBack={() => router.back()} />
        <div style={{ padding: '28px 28px 40px', display: 'flex', flexDirection: 'column', gap: 24 }}>
          <div style={{ textAlign: 'center', marginTop: 40 }}>
            <div className="serif" style={{ fontSize: 20, lineHeight: 1.5, marginBottom: 16 }}>
              아직 기록된<br />대화가 없어요.
            </div>
            <div style={{ fontSize: 14, color: 'var(--L-sub)', lineHeight: 1.6 }}>
              첫 이야기를 시작해보세요.
            </div>
          </div>

          <button
            onClick={() => router.push('/session/new')}
            className="btn-L"
            style={{ width: '100%', marginTop: 24 }}
          >
            이야기 시작하기
          </button>
        </div>
      </PhoneFrame>
    );
  }

  // History list
  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="지나온 이야기" tone="L" onBack={() => router.back()} />
      <div style={{ padding: '8px 28px 40px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {history.map((item, idx) => {
          const dateStr = item.completedAt
            ? new Date(item.completedAt).toLocaleDateString('ko-KR', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
              })
            : '';

          return (
            <div
              key={idx}
              className="letter-card"
              onClick={() => router.push(`/session/result/${item.id}`)}
              style={{
                cursor: 'pointer',
                padding: '14px 16px',
              }}
            >
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginBottom: 6 }}>
                {dateStr}
              </div>
              <div
                className="serif"
                style={{
                  fontSize: 15,
                  color: 'var(--L-ink)',
                  fontWeight: 500,
                  marginBottom: 8,
                }}
              >
                {item.partnerNickname || '익명'}님과의 대화
              </div>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                <span
                  style={{
                    fontSize: '11px',
                    background: 'var(--L-card)',
                    border: '1px solid var(--L-border)',
                    borderRadius: '3px',
                    padding: '4px 8px',
                    color: 'var(--L-sub)',
                  }}
                >
                  {RELATION_TYPE_LABEL[item.relationType]}
                </span>
                {item.conflictType && (
                  <span
                    style={{
                      fontSize: '11px',
                      background: 'var(--L-card)',
                      border: '1px solid var(--L-border)',
                      borderRadius: '3px',
                      padding: '4px 8px',
                      color: 'var(--L-sub)',
                    }}
                  >
                    {item.conflictType}
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </PhoneFrame>
  );
}
