// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (OnboardingSlider)
'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { STYLE_MOTIF } from '@/components/shared/Motif';
import { COMMUNICATION_STYLES } from '@/lib/constants/communicationStyles';
import { useUserStore } from '@/lib/store/userStore';
import type { CommunicationStyle } from '@/lib/types';

export default function OnboardingResultPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const user = useUserStore((s) => s.user);
  const setOnboardingCompleted = useUserStore((s) => s.setOnboardingCompleted);
  const [mounted, setMounted] = useState(false);

  const nextPath = searchParams.get('next') ?? '/session/new';

  useEffect(() => {
    setMounted(true);
    if (!user?.communicationStyle) {
      router.replace('/onboarding/intro');
      return;
    }
    if (!user.onboardingCompletedAt) {
      setOnboardingCompleted();
    }
  }, [user, router, setOnboardingCompleted]);

  if (!user?.communicationStyle) {
    return null;
  }

  const style = user.communicationStyle;
  const styleDef = COMMUNICATION_STYLES[style];
  const MotifIcon = STYLE_MOTIF[style];
  const hasMbti = !!user.mbtiType;
  const nextParam = `?next=${encodeURIComponent(nextPath)}`;

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="" back={true} onBack={() => router.back()} />
      <div style={{ padding: '32px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>

        {/* Motif */}
        <div
          style={{
            width: 88,
            height: 88,
            borderRadius: '50%',
            background: 'var(--P-a)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 24,
            animation: mounted ? 'fade-in-up 0.6s ease-out' : 'none',
            opacity: mounted ? 1 : 0,
          }}
        >
          <MotifIcon size={52} color="white" />
        </div>

        {/* MBTI badge */}
        {hasMbti && user.mbtiType && (
          <div
            style={{
              fontSize: 12,
              color: 'var(--L-accent)',
              background: 'color-mix(in srgb, var(--L-accent) 10%, transparent)',
              borderRadius: 20,
              padding: '4px 12px',
              marginBottom: 10,
              animation: mounted ? 'fade-in-up 0.6s ease-out 0.05s both' : 'none',
            }}
          >
            MBTI {user.mbtiType} 기준
          </div>
        )}

        {/* Style label */}
        <div
          className="serif"
          style={{
            fontSize: 24,
            lineHeight: 1.4,
            marginBottom: 10,
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.1s both' : 'none',
            textAlign: 'center',
            color: 'var(--L-ink)',
          }}
        >
          {styleDef.label}
        </div>

        {/* Description */}
        <div
          style={{
            fontSize: 13,
            color: 'var(--L-sub)',
            textAlign: 'center',
            marginBottom: 24,
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.2s both' : 'none',
          }}
        >
          {styleDef.description}
        </div>

        {/* Strengths */}
        <div
          style={{
            width: '100%',
            marginBottom: 16,
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.3s both' : 'none',
          }}
        >
          <div className="quote-it" style={{ fontSize: 12, marginBottom: 8 }}>
            이런 점이 좋아요
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            {styleDef.strengths.map((s, i) => (
              <div key={i} style={{ fontSize: 13, color: 'var(--L-ink)', lineHeight: 1.5 }}>
                · {s}
              </div>
            ))}
          </div>
        </div>

        {/* Caution */}
        <div
          style={{
            width: '100%',
            marginBottom: 28,
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.4s both' : 'none',
          }}
        >
          <div className="quote-it" style={{ fontSize: 12, marginBottom: 8 }}>
            기억해주세요
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            {styleDef.caution.map((c, i) => (
              <div key={i} style={{ fontSize: 13, color: 'var(--L-ink)', lineHeight: 1.5 }}>
                · {c}
              </div>
            ))}
          </div>
        </div>

        {/* MBTI enhancement section — shown only when MBTI not yet added */}
        {!hasMbti && (
          <div
            style={{
              width: '100%',
              marginBottom: 20,
              animation: mounted ? 'fade-in-up 0.6s ease-out 0.5s both' : 'none',
            }}
          >
            <div
              style={{
                borderTop: '1px solid var(--L-rule)',
                paddingTop: 20,
                marginBottom: 14,
              }}
            >
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--L-ink)', marginBottom: 4 }}>
                정확도를 높이려면 MBTI를 추가해주세요
              </div>
              <div style={{ fontSize: 12, color: 'var(--L-sub)', lineHeight: 1.6 }}>
                10문항 결과에 MBTI를 더하면 더 세밀한 중재를 받을 수 있어요.
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[
                {
                  title: 'MBTI 직접 입력',
                  desc: '알고 있는 유형을 슬라이더로 조절',
                  href: `/onboarding/mbti-input${nextParam}`,
                },
                {
                  title: 'MBTI 유형 검사 · 60문항',
                  desc: '문항에 답하면 유형이 자동 분석돼요',
                  href: `/onboarding/mbti-test${nextParam}`,
                },
              ].map((opt) => (
                <button
                  key={opt.title}
                  onClick={() => router.push(opt.href)}
                  style={{
                    width: '100%',
                    background: 'var(--L-bg)',
                    border: '1.5px solid var(--L-rule)',
                    borderRadius: 12,
                    padding: '14px 18px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    cursor: 'pointer',
                    textAlign: 'left',
                  }}
                >
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--L-ink)', marginBottom: 2 }}>
                      {opt.title}
                    </div>
                    <div style={{ fontSize: 12, color: 'var(--L-sub)' }}>{opt.desc}</div>
                  </div>
                  <span style={{ color: 'var(--L-sub)', fontSize: 16, marginLeft: 12, flexShrink: 0 }}>›</span>
                </button>
              ))}
            </div>
          </div>
        )}

        {/* CTA */}
        <div
          style={{
            width: '100%',
            marginTop: 'auto',
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.55s both' : 'none',
          }}
        >
          <button
            className={hasMbti ? 'btn-L' : 'btn-L ghost'}
            style={{ width: '100%' }}
            onClick={() => router.push(nextPath)}
          >
            완료하기
          </button>
        </div>
      </div>
    </PhoneFrame>
  );
}
