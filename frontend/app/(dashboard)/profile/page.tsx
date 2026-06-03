'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useUserStore, useHasHydrated } from '@/lib/store/userStore';

type Tab = 'mine' | 'voted' | 'saved';

export default function ProfilePage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const hasHydrated = useHasHydrated();
  const userId = user?.id;
  const isGuest = user?.isGuest;

  const [tab, setTab] = useState<Tab>('mine');

  useEffect(() => {
    if (hasHydrated && (!userId || isGuest)) {
      router.push('/login');
    }
  }, [hasHydrated, userId, isGuest, router]);

  if (!hasHydrated || !user) return null;

  const avatarChar = (user.nickname || '?').charAt(0);
  const TABS: { key: Tab; label: string }[] = [
    { key: 'mine',  label: '내 사연' },
    { key: 'voted', label: '투표한 글' },
    { key: 'saved', label: '저장' },
  ];

  return (
    <div style={{ background: 'var(--L-bg)', minHeight: '100vh', paddingBottom: 80 }}>
      <div style={{ maxWidth: 640, margin: '0 auto', padding: '18px 22px 0' }}>

        {/* 헤더 */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 18 }}>
          <span style={{ fontFamily: 'var(--font-serif)', fontSize: 17, fontWeight: 500, color: 'var(--L-ink)' }}>
            마이페이지
          </span>
        </div>

        {/* 프로필 행 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 22 }}>
          <span style={{
            width: 46, height: 46, borderRadius: '50%',
            background: 'var(--P-a)',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: 'var(--font-serif)', fontSize: 18, color: '#fff',
            flexShrink: 0,
          }}>
            {avatarChar}
          </span>
          <div>
            <div style={{ fontSize: 15, fontWeight: 500, color: 'var(--L-ink)' }}>{user.nickname}</div>
            <div style={{ fontSize: 12, color: 'var(--L-sub)', marginTop: 3 }}>
              {user.nickname}
            </div>
          </div>
        </div>

        {/* 탭 */}
        <div style={{ display: 'flex', gap: 18, borderBottom: '1px solid var(--L-border)' }}>
          {TABS.map(({ key, label }) => {
            const on = tab === key;
            return (
              <span
                key={key}
                onClick={() => setTab(key)}
                style={{
                  cursor: 'pointer',
                  fontSize: 13,
                  fontWeight: on ? 500 : 400,
                  color: on ? 'var(--L-ink)' : 'var(--L-sub)',
                  paddingBottom: 10,
                  borderBottom: `2px solid ${on ? 'var(--L-ink)' : 'transparent'}`,
                  transition: 'color 0.15s',
                }}
              >
                {label}
              </span>
            );
          })}
        </div>

        {/* ── 내 사연 ── */}
        {tab === 'mine' && (
          <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
            준비 중입니다
          </div>
        )}

        {/* ── 투표한 글 ── */}
        {tab === 'voted' && (
          <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
            준비 중입니다
          </div>
        )}

        {/* ── 저장 ── */}
        {tab === 'saved' && (
          <div style={{ textAlign: 'center', padding: '32px 0', fontSize: 13, color: 'var(--L-sub)' }}>
            준비 중입니다
          </div>
        )}

      </div>
    </div>
  );
}
