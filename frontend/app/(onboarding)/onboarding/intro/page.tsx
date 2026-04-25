'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';

export default function OnboardingIntroPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useUserStore((s) => s.user);

  const nextParam = searchParams.get('next') ? `?next=${searchParams.get('next')}` : '';

  if (user?.isGuest && user?.onboardingAnswers) {
    router.replace('/onboarding/result');
    return null;
  }

  const methods = [
    {
      id: 'test',
      emoji: '🎯',
      title: '10문항 검사',
      desc: '갈등 상황 기반 질문 · 약 2분',
      href: `/onboarding${nextParam}`,
    },
    {
      id: 'mbti-input',
      emoji: '🔤',
      title: 'MBTI 직접 입력',
      desc: '알고 있는 유형을 바로 입력',
      href: `/onboarding/mbti-input${nextParam}`,
    },
    {
      id: 'mbti-test',
      emoji: '📋',
      title: 'MBTI 간이 검사',
      desc: '8문항으로 유형 파악 · 약 3분',
      href: `/onboarding/mbti-test${nextParam}`,
    },
  ];

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="나의 대화 성향" back={false} />
      <div style={{ padding: '28px 28px 32px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div
          className="serif"
          style={{ fontSize: 21, lineHeight: 1.55, marginBottom: 8, textAlign: 'center' }}
        >
          성향 파악 방식을<br />선택해주세요
        </div>
        <div
          style={{ fontSize: 13, color: 'var(--L-sub)', textAlign: 'center', marginBottom: 32, lineHeight: 1.6 }}
        >
          검사를 완료하면 더 정확한 중재를<br />받을 수 있어요
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {methods.map((m) => (
            <button
              key={m.id}
              onClick={() => router.push(m.href)}
              style={{
                width: '100%',
                background: 'var(--L-bg)',
                border: '1.5px solid var(--L-rule)',
                borderRadius: 14,
                padding: '18px 20px',
                display: 'flex',
                alignItems: 'center',
                gap: 16,
                cursor: 'pointer',
                textAlign: 'left',
                transition: 'border-color 0.15s, background 0.15s',
              }}
              onMouseEnter={(e) => {
                (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--L-accent)';
                (e.currentTarget as HTMLButtonElement).style.background = 'var(--L-accent-soft, color-mix(in srgb, var(--L-accent) 8%, transparent))';
              }}
              onMouseLeave={(e) => {
                (e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--L-rule)';
                (e.currentTarget as HTMLButtonElement).style.background = 'var(--L-bg)';
              }}
            >
              <span style={{ fontSize: 28, lineHeight: 1 }}>{m.emoji}</span>
              <div>
                <div style={{ fontSize: 15, fontWeight: 600, color: 'var(--L-ink)', marginBottom: 3 }}>
                  {m.title}
                </div>
                <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>{m.desc}</div>
              </div>
              <span style={{ marginLeft: 'auto', color: 'var(--L-sub)', fontSize: 16 }}>›</span>
            </button>
          ))}
        </div>
      </div>
    </PhoneFrame>
  );
}
