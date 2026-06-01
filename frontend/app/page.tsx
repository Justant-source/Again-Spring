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

  // 활성 세션 폴링 — 로그인 사용자 전체 (게스트도 canResumeOldSession:true 정책 준수)
  useEffect(() => {
    if (!user) return;
    api.get('/api/users/me/history').then(r => {
      const active = (r.data as any[]).find(s => ACTIVE_STATUSES.has(s.status));
      setActiveSessionId(active?.id ?? null);
    }).catch(() => { });
  }, [user]);

  if (!mounted) return null;

  const handleStartSession = () => {
    // 활성 세션이 있으면 바로 이어서 대화 (회원·게스트 공통)
    if (activeSessionId) {
      router.push(`/session/chat/${activeSessionId}`);
      return;
    }
    // 비로그인: 게스트로 바로 진입 (로그인 강제 제거)
    if (!user) {
      router.push('/guest?next=/session/new');
      return;
    }
    router.push('/session/new');
  };

  const perms = permissionsFor(user);
  const showAdminEntry = perms.ui.showAdminEntryButton;
  const showMarketingEntry = perms.admin.canAccessMarketing;
  const showChatEntry = perms.ui.showLandingChatEntry;

  return (
    <PhoneFrame tone="L">
      <div className="flex flex-col flex-1 px-7 pt-6 pb-5">
        {/* 헤더: 로고만 — 내비·프로필은 하단 5탭으로 */}
        <div className="flex items-center">
          <Logo />
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
                  <li>· 상대와 직접 말하지 않고, 각자 중재자와 대화해요</li>
                  <li>· 옳고 그름이 아니라 서로의 마음을 봐요</li>
                </ul>
              </div>
            </div>

            {/* 커뮤니티 진입 카드 */}
            <div
              style={{ marginTop: 20, borderRadius: 10, border: '1px solid var(--L-border)', overflow: 'hidden' }}
            >
              <Link
                href="/community"
                style={{ textDecoration: 'none', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 16px', background: 'var(--P-bg, #FBF3EC)' }}
              >
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--P-ink, #5C4030)' }}>
                    커뮤니티 사연 보기
                  </div>
                  <div style={{ fontSize: 11, marginTop: 3, color: 'var(--P-sub, #A08670)' }}>
                    다른 사람들의 갈등 사연 · 투표 · AI 배심원
                  </div>
                </div>
                <span style={{ color: 'var(--P-sub)', fontSize: 16 }}>›</span>
              </Link>
              <Link
                href="/three-way/new"
                style={{ textDecoration: 'none', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 16px', background: 'white', borderTop: '1px solid var(--L-border)' }}
              >
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--L-ink, #2B2B2B)' }}>
                    상대방과 함께 대화하기
                  </div>
                  <div style={{ fontSize: 11, marginTop: 3, color: 'var(--L-sub, #8A7F6B)' }}>
                    3자 대화 · AI 중재자 · 초대 링크 공유
                  </div>
                </div>
                <span style={{ color: 'var(--L-sub)', fontSize: 16 }}>›</span>
              </Link>
            </div>

            <div className="flex flex-col gap-2 pb-2" style={{ paddingTop: 24 }}>
              <button
                onClick={handleStartSession}
                className="btn-L text-center"
              >
                대화 시작
              </button>
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
