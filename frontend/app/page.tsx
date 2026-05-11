// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (LandingScreen)
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
    }).catch(() => {});
  }, [user]);

  if (!mounted) return null;

  const handleStartSession = () => {
    if (!user) {
      router.push('/login?next=/session/new');
      return;
    }
    if (!user.onboardingCompletedAt || !user.communicationStyle) {
      router.push('/onboarding/intro?next=/session/new');
      return;
    }
    router.push('/session/new');
  };

  const perms = permissionsFor(user);
  const showAdminEntry = perms.ui.showAdminEntryButton;
  const showChatEntry = perms.ui.showLandingChatEntry;
  const showHistoryMenu = perms.ui.showHistoryMenu;
  const needsOnboarding =
    !!user && showChatEntry &&
    (!user.onboardingCompletedAt || !user.communicationStyle);

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

        {/* 이어서 대화하기 배너 (활성 세션 있을 때, 채팅 진입 가능 등급만) */}
        {showChatEntry && activeSessionId && !needsOnboarding && (
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

        {/* 온보딩 안내 배너 (로그인 사용자, 미완료 시, 채팅 진입 가능 등급만) */}
        {showChatEntry && needsOnboarding && (
          <div
            style={{
              marginTop: 16,
              padding: '12px 16px',
              background: 'var(--L-card)',
              border: '1px solid var(--L-border)',
              borderRadius: 8,
              fontSize: 12,
              color: 'var(--L-sub)',
              lineHeight: 1.6,
            }}
          >
            <span style={{ color: 'var(--L-ink)', fontWeight: 500 }}>성격검사를 완료하면 더 정확한 중재를 받을 수 있어요.</span>
            {' '}
            <Link
              href="/onboarding/intro"
              style={{ color: 'var(--L-ink)', textDecoration: 'underline' }}
            >
              지금 검사하기
            </Link>
          </div>
        )}

        {/* 일반 사용자 채팅 진입 본문 — admin은 노출 안 함 */}
        {showChatEntry ? (
          <>
            <div className="flex-1 mt-20">
              <div className="text-[13px] mb-3.5" style={{ color: 'var(--L-sub)' }}>
                중재자와 대화
              </div>
              <h1
                className="serif"
                style={{ fontSize: 32, lineHeight: 1.35, letterSpacing: '-0.01em' }}
              >
                마음을<br />정리해요.
              </h1>
              <p className="mt-5 text-[14px] leading-[1.7]" style={{ color: 'var(--L-sub)' }}>
                혼자서도, 함께라도 가능해요.<br />
                5분이면 충분합니다.
              </p>

              <div className="mt-12 letter-card" style={{ padding: 20 }}>
                <div className="quote-it" style={{ fontSize: 12, marginBottom: 10 }}>
                  다시봄은 이런 도구예요
                </div>
                <ul className="serif" style={{ fontSize: 13, lineHeight: 1.9 }}>
                  <li>· 중재자와 대화로 마음을 정리해요</li>
                  <li>· 옳고 그름이 아니라 서로의 마음을 봐요</li>
                  <li>· 이야기는 30일 후 자동으로 사라져요</li>
                </ul>
              </div>
            </div>

            <div className="flex flex-col gap-2 pb-2 pt-4">
              <button
                onClick={handleStartSession}
                disabled={!user || needsOnboarding}
                className="btn-L text-center"
              >
                {needsOnboarding ? '먼저 10문항을 등록해주세요' : '마음 옮겨 적기 시작'}
              </button>
              {needsOnboarding && (
                <Link
                  href="/onboarding/intro?next=/session/new"
                  className="text-center text-[12px] mt-1"
                  style={{ color: 'var(--L-ink)', textDecoration: 'underline' }}
                >
                  10문항 시작하기
                </Link>
              )}
              {!user && (
                <Link href="/guest" className="text-center text-[12px] mt-1" style={{ color: 'var(--L-sub)' }}>
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
