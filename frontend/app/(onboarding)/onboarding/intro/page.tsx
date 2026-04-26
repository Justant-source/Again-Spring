'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';

export default function OnboardingIntroPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useUserStore((s) => s.user);

  const nextParam = searchParams.get('next') ? `?next=${searchParams.get('next')}` : '';

  if (user?.communicationStyle) {
    router.replace(`/onboarding/result${nextParam}`);
    return null;
  }

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="나의 대화 성향" back={false} />
      <div style={{ padding: '48px 28px 40px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div
          className="serif"
          style={{ fontSize: 22, lineHeight: 1.6, marginBottom: 16, color: 'var(--L-ink)' }}
        >
          갈등 상황에서의<br />나의 대화 패턴을<br />파악해보세요
        </div>
        <div
          style={{
            fontSize: 13,
            color: 'var(--L-sub)',
            lineHeight: 1.9,
            marginBottom: 40,
          }}
        >
          10문항 검사는 필수로 진행돼요.<br />
          검사 후 MBTI를 추가하면 더 정확한 중재를 받을 수 있어요.
        </div>

        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
            padding: '20px 0',
            borderTop: '1px solid var(--L-rule)',
            borderBottom: '1px solid var(--L-rule)',
            marginBottom: 40,
          }}
        >
          {[
            { step: '1단계', title: '10문항 검사', desc: '갈등 상황 기반 · 약 2분 · 필수' },
            { step: '2단계', title: 'MBTI 추가', desc: 'MBTI 직접 입력 또는 60문항 검사 · 선택' },
          ].map((item) => (
            <div key={item.step} style={{ display: 'flex', alignItems: 'flex-start', gap: 14, padding: '8px 0' }}>
              <div
                style={{
                  fontSize: 10,
                  fontWeight: 600,
                  color: 'var(--L-accent)',
                  background: 'color-mix(in srgb, var(--L-accent) 10%, transparent)',
                  borderRadius: 5,
                  padding: '3px 7px',
                  flexShrink: 0,
                  marginTop: 2,
                }}
              >
                {item.step}
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--L-ink)', marginBottom: 2 }}>
                  {item.title}
                </div>
                <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>{item.desc}</div>
              </div>
            </div>
          ))}
        </div>

        <button
          className="btn-L"
          onClick={() => router.push(`/onboarding${nextParam}`)}
          style={{ marginTop: 'auto' }}
        >
          10문항 시작하기
        </button>
      </div>
    </PhoneFrame>
  );
}
