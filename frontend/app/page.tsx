'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { PhoneFrame } from '@/components/shared/PhoneFrame';
import { Logo } from '@/components/shared/Logo';
import { Footer } from '@/components/shared/Footer';
import { BrandBar } from '@/components/community/c3';
import { useUserStore } from '@/lib/store/userStore';
import { useGuestInit } from '@/lib/hooks/useGuestInit';
import { permissionsFor } from '@/lib/constants/userPermissions';

export default function LandingPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const [mounted, setMounted] = useState(false);
  useGuestInit();

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!mounted) return null;

  const perms = permissionsFor(user);
  const showAdminEntry = perms.ui.showAdminEntryButton;
  const showMarketingEntry = perms.admin.canAccessMarketing;

  return (
    <PhoneFrame tone="L">
      <div className="flex flex-col flex-1 px-7 pt-6 pb-5">
        {/* 헤더: 다시봄 + 우측 유저 칩 */}
        <BrandBar title="다시봄" user={user} />

        {/* 관리자 모드 진입 카드 — user-permissions.json의 ui.showAdminEntryButton */}
        {showAdminEntry && (
          <button
            onClick={() => router.push('/admin')}
            style={{
              marginTop: 16,
              width: '100%',
              padding: '14px 18px',
              background: 'var(--L-ink)',
              color: 'var(--L-card)',
              border: 'none',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, letterSpacing: 0.3 }}>
                관리자 모드
              </div>
              <div style={{ fontSize: 11, marginTop: 3, opacity: 0.75 }}>
                대시보드 · 의견함 · 사용자 · 위기 모니터링
              </div>
            </div>
            <span style={{ fontSize: 18, opacity: 0.85 }}>›</span>
          </button>
        )}

        {/* 마케팅 모드 진입 카드 — admin.canAccessMarketing (dev 전용) */}
        {showMarketingEntry && (
          <button
            onClick={() => router.push('/admin/marketing')}
            style={{
              marginTop: 8,
              width: '100%',
              padding: '14px 18px',
              background: '#2d4a7a',
              color: 'white',
              border: 'none',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, letterSpacing: 0.3 }}>
                마케팅 모드
              </div>
              <div style={{ fontSize: 11, marginTop: 3, opacity: 0.75 }}>
                사연 · 시뮬레이션 · 콘텐츠 생성 · 비용
              </div>
            </div>
            <span style={{ fontSize: 18, opacity: 0.85 }}>›</span>
          </button>
        )}

        {/* C3 광장형 랜딩 본문 */}
        <div className="mt-8 flex flex-col flex-1">
          <div className="text-[12px] mb-2.5" style={{ color: 'var(--L-sub)' }}>
            혼자 끙끙 앓지 마세요
          </div>
          <h1
            className="serif"
            style={{
              fontSize: 30,
              lineHeight: 1.35,
              letterSpacing: '-0.01em',
              marginBottom: 16,
            }}
          >
            나의 갈등,<br />혼자 판단하기<br />어려울 때.
          </h1>
          <p
            className="text-[14px]"
            style={{
              color: 'var(--L-sub)',
              lineHeight: 1.75,
              marginBottom: 24,
            }}
          >
            익명의 여론이 여러 시선을 빌려드려요.
            <br />
            혼자, 또는 상대와 함께 풀어가요.
          </p>

          {/* 통계 블록 */}
          <div
            className="flex items-center justify-center gap-4 mb-8"
            style={{ padding: '12px 0' }}
          >
            <div style={{ textAlign: 'center', flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--L-ink)' }}>
                12,840
              </div>
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 2 }}>
                오늘 모인 시선
              </div>
            </div>
            <div
              style={{
                width: 1,
                height: 24,
                backgroundColor: 'var(--L-sub)',
                opacity: 0.2,
              }}
            />
            <div style={{ textAlign: 'center', flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--L-ink)' }}>
                3분
              </div>
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 2 }}>
                평균 소요
              </div>
            </div>
          </div>

          {/* 스페이서 */}
          <div className="flex-1" />

          {/* 하단 버튼 */}
          <button
            onClick={() => router.push('/community')}
            style={{
              width: '100%',
              padding: '12px 0',
              background: 'var(--L-ink)',
              color: 'var(--L-card)',
              border: 'none',
              borderRadius: 6,
              fontSize: 14,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            다시봄 광장
          </button>
        </div>
      </div>
      <Footer />
    </PhoneFrame>
  );
}
