// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (OnboardingSlider)
'use client';

import { useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { useUserStore } from '@/lib/store/userStore';

export default function OnboardingIntroPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);

  // If guest and already has answers, skip to result
  if (user?.isGuest && user?.onboardingAnswers) {
    router.replace('/onboarding/result');
    return null;
  }

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="나의 대화 성향" back={false} />
      <div style={{ padding: '28px', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
        <div className="serif" style={{ fontSize: 22, lineHeight: 1.5, marginBottom: 28, textAlign: 'center' }}>
          10개 문장으로<br />
          당신의 대화 결을<br />살펴볼게요
        </div>

        <div style={{ fontSize: 13, color: 'var(--L-sub)', lineHeight: 1.7, textAlign: 'center', marginBottom: 40 }}>
          2분 정도 걸려요
        </div>

        <button
          onClick={() => router.push('/onboarding')}
          className="btn-L"
          style={{ marginTop: 'auto' }}
        >
          시작하기
        </button>
      </div>
    </PhoneFrame>
  );
}
