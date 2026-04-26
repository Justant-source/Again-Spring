// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (LandingScreen)
'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { PhoneFrame } from '@/components/shared/PhoneFrame';
import { Logo } from '@/components/shared/Logo';
import { useUserStore } from '@/lib/store/userStore';

const CHIPS = ['연인', '부부', '친구', '가족', '부모자식'];

export default function LandingPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (mounted && !user) {
      router.replace('/login');
    }
  }, [mounted, user, router]);

  if (!mounted || !user) return null;

  const handleStartSession = () => {
    if (user && !user.isGuest && !user.onboardingCompletedAt) {
      // 서비스 생성자는 온보딩 필수
      router.push('/onboarding/intro?next=/session/new');
      return;
    }
    router.push('/session/new');
  };

  const needsOnboarding = user && !user.isGuest && !user.onboardingCompletedAt;

  return (
    <PhoneFrame tone="L">
      <div className="flex flex-col flex-1 px-7 pt-6 pb-5">
        <div className="flex items-center justify-between">
          <Logo />
          {user ? (
            <Link href="/profile" className="text-[12px]" style={{ color: 'var(--L-sub)' }}>
              {user.nickname}
            </Link>
          ) : (
            <Link href="/login" className="text-[12px]" style={{ color: 'var(--L-sub)' }}>
              로그인
            </Link>
          )}
        </div>

        {/* 온보딩 안내 배너 (로그인 사용자, 미완료 시) */}
        {needsOnboarding && (
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

        <div className="flex-1 mt-20">
          <div className="text-[13px] mb-3.5" style={{ color: 'var(--L-sub)' }}>
            관계 회복 AI 중재자
          </div>
          <h1
            className="serif"
            style={{ fontSize: 32, lineHeight: 1.35, letterSpacing: '-0.01em' }}
          >
            지금, 누군가와<br />서운한 일이<br />있으신가요.
          </h1>
          <p className="mt-5 text-[14px] leading-[1.7]" style={{ color: 'var(--L-sub)' }}>
            판결이 아니라, 중재입니다.<br />
            두 사람의 마음을 차분히 정리해드려요.
          </p>

          <div className="mt-9 flex gap-2 flex-wrap">
            {CHIPS.map((c) => (
              <span key={c} className="chip-L">
                {c}
              </span>
            ))}
          </div>

          <div className="mt-12 letter-card" style={{ padding: 20 }}>
            <div className="quote-it" style={{ fontSize: 12, marginBottom: 10 }}>
              다시봄은 이런 마음입니다
            </div>
            <ul className="serif" style={{ fontSize: 13, lineHeight: 1.9 }}>
              <li>· 원문은 두 분 모두 적으신 후에 공개돼요</li>
              <li>· 옳고 그름이 아니라 서로의 욕구를 봐요</li>
              <li>· 이야기는 30일 후 자동으로 사라져요</li>
            </ul>
          </div>
        </div>

        <div className="flex flex-col gap-2 pb-2 pt-4">
          <button onClick={handleStartSession} className="btn-L text-center">
            이야기 시작하기
          </button>
          <Link href="/guest" className="text-center text-[12px] mt-1" style={{ color: 'var(--L-sub)' }}>
            게스트로 둘러보기
          </Link>
        </div>
      </div>
    </PhoneFrame>
  );
}
