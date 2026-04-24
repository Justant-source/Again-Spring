// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (OnboardingSlider)
'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { STYLE_MOTIF } from '@/components/shared/Motif';
import { COMMUNICATION_STYLES } from '@/lib/constants/communicationStyles';
import { useUserStore } from '@/lib/store/userStore';
import type { CommunicationStyle } from '@/lib/types';

export default function OnboardingResultPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    if (!user?.communicationStyle) {
      router.replace('/onboarding');
    }
  }, [user, router]);

  if (!user?.communicationStyle) {
    return null;
  }

  const style = user.communicationStyle;
  const styleDef = COMMUNICATION_STYLES[style];
  const MotifIcon = STYLE_MOTIF[style];

  const handleCopy = () => {
    const text = `나의 대화 성향은 "${styleDef.label}"이에요.\n\n${styleDef.description}`;
    navigator.clipboard.writeText(text).catch(() => {});
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="" back={false} />
      <div style={{ padding: '40px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        {/* Motif circle with fade-in-up animation */}
        <div
          style={{
            width: 96,
            height: 96,
            borderRadius: '50%',
            background: 'var(--P-a)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 28,
            animation: mounted ? 'fade-in-up 0.6s ease-out' : 'none',
            opacity: mounted ? 1 : 0,
          }}
        >
          <MotifIcon size={56} color="white" />
        </div>

        {/* Label */}
        <div
          className="serif"
          style={{
            fontSize: 26,
            lineHeight: 1.4,
            marginBottom: 12,
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.1s both' : 'none',
            textAlign: 'center',
          }}
        >
          {styleDef.emoji} {styleDef.label}
        </div>

        {/* Description */}
        <div
          style={{
            fontSize: 13,
            color: 'var(--L-sub)',
            textAlign: 'center',
            marginBottom: 28,
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.2s both' : 'none',
          }}
        >
          {styleDef.description}
        </div>

        {/* Strengths */}
        <div
          style={{
            width: '100%',
            marginBottom: 20,
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.3s both' : 'none',
          }}
        >
          <div className="quote-it" style={{ fontSize: 12, marginBottom: 10 }}>
            이런 점이 좋아요
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {styleDef.strengths.map((strength, i) => (
              <div key={i} style={{ fontSize: 13, color: 'var(--L-ink)', lineHeight: 1.5 }}>
                · {strength}
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
          <div className="quote-it" style={{ fontSize: 12, marginBottom: 10 }}>
            기억해주세요
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {styleDef.caution.map((item, i) => (
              <div key={i} style={{ fontSize: 13, color: 'var(--L-ink)', lineHeight: 1.5 }}>
                · {item}
              </div>
            ))}
          </div>
        </div>

        {/* CTA Buttons */}
        <div
          style={{
            width: '100%',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            marginTop: 'auto',
            animation: mounted ? 'fade-in-up 0.6s ease-out 0.5s both' : 'none',
          }}
        >
          <Link href="/session/new" className="btn-L" style={{ textAlign: 'center', textDecoration: 'none', display: 'block' }}>
            세션 시작하기
          </Link>
          <button
            onClick={handleCopy}
            className="btn-L ghost"
            style={{ width: '100%' }}
          >
            공유하기
          </button>
          {user.isGuest && (
            <button
              onClick={() => router.push('/session/new')}
              className="btn-L ghost"
              style={{ width: '100%', fontSize: 12 }}
            >
              건너뛰기
            </button>
          )}
        </div>
      </div>
    </PhoneFrame>
  );
}
