'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { PhoneFrame } from '@/components/shared/PhoneFrame';
import { Logo } from '@/components/shared/Logo';
import { Footer } from '@/components/shared/Footer';
import { useUserStore } from '@/lib/store/userStore';
import { permissionsFor } from '@/lib/constants/userPermissions';
import { api } from '@/lib/api/client';

const ACTIVE_STATUSES = new Set(['chatting_solo', 'chatting_duo', 'awaiting_finalization']);

export default function LandingPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const [mounted, setMounted] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);

  useEffect(() => {
    setMounted(true);
  }, []);

  // 활성 세션 폴링 — 게스트 외 모든 등급 (ADMIN도 일반 사용자처럼 동작)
  useEffect(() => {
    if (!user || user.isGuest) return;
    api.get('/api/users/me/history').then(r => {
      const active = (r.data as any[]).find(s => ACTIVE_STATUSES.has(s.status));
      setActiveSessionId(active?.id ?? null);
    }).catch(() => { });
  }, [user]);

  if (!mounted) return null;

  const handleStartSession = () => {
    if (!user) {
      router.push('/login?next=/session/new');
      return;
    }
    router.push('/session/new');
  };

  const perms = permissionsFor(user);
  const showAdminEntry = perms.ui.showAdminEntryButton;
  const showMarketingEntry = perms.admin.canAccessMarketing;
  const showChatEntry = perms.ui.showLandingChatEntry;
  const showHistoryMenu = perms.ui.showHistoryMenu;

  return (
    <PhoneFrame tone="L">
      <div className="flex flex-col flex-1 px-7 pt-6 pb-5">
        <div className="flex items-center justify-between">
          <Logo />
          {user ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {showHistoryMenu && (
                <Link href="/history" className="text-[12px]" style={{ color: 'var(--L-sub)' }}>
                  지난 대화
                </Link>
              )}
              <Link href="/profile" className="text-[12px]" style={{ color: 'var(--L-sub)' }}>
                {user.nickname}
              </Link>
            </div>
          ) : (
            <Link href="/login" className="text-[12px]" style={{ color: 'var(--L-sub)' }}>
              로그인
            </Link>
          )}
        </div>

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

        {/* 이어서 대화하기 배너 (활성 세션 있을 때, 채팅 진입 가능 등급만) */}
        {showChatEntry && activeSessionId && (
          <button
            onClick={() => router.push(`/session/chat/${activeSessionId}`)}
            style={{
              marginTop: 16,
              width: '100%',
              padding: '12px 16px',
              background: 'var(--L-card)',
              border: '1px solid var(--L-rule)',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              cursor: 'pointer',
              textAlign: 'left',
            }}
          >
            <div>
              <div style={{ fontSize: 13, color: 'var(--L-ink)', fontWeight: 500 }}>이어서 대화하기</div>
              <div style={{ fontSize: 11, color: 'var(--L-sub)', marginTop: 2 }}>진행 중인 대화가 있어요</div>
            </div>
            <span style={{ color: 'var(--L-sub)', fontSize: 18 }}>›</span>
          </button>
        )}

        {/* 일반 사용자 채팅 진입 본문 — admin은 노출 안 함 */}
        {showChatEntry ? (
          <>
            <div className="mt-6">
              <div className="text-[13px] mb-2.5" style={{ color: 'var(--L-sub)' }}>
                중재자와 대화
              </div>
              <h1
                className="serif"
                style={{ fontSize: 32, lineHeight: 1.35, letterSpacing: '-0.01em' }}
              >
                마음을<br />정리해요.
              </h1>
              <p className="mt-3 text-[14px] leading-[1.7]" style={{ color: 'var(--L-sub)' }}>
                5분이면 충분합니다.
              </p>

              <div className="mt-5 letter-card" style={{ padding: 16 }}>
                <div className="quote-it" style={{ fontSize: 12, marginBottom: 8 }}>
                  다시봄은 이런 도구예요
                </div>
                <ul className="serif" style={{ fontSize: 13, lineHeight: 1.8 }}>
                  <li>· 대화로 같이 마음을 정리해요</li>
                  <li>· 옳고 그름이 아니라 서로의 마음을 봐요</li>
                </ul>
              </div>
            </div>

            <div className="flex flex-col gap-2 pb-2" style={{ paddingTop: 50 }}>
              <button
                onClick={handleStartSession}
                disabled={!user}
                className="btn-L text-center"
              >
                대화 시작
              </button>
              {!user && (
                <Link href="/guest" className="btn-L ghost" style={{ textAlign: 'center', textDecoration: 'none', display: 'block' }}>
                  게스트로 둘러보기
                </Link>
              )}
            </div>
          </>
        ) : (
          // admin 등 채팅 진입 비대상 — 빈 영역으로 [관리자 모드] 카드만 부각
          <div className="flex-1" />
        )}
      </div>
      <Footer />
    </PhoneFrame>
  );
}
