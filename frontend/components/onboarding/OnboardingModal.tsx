'use client';

import { useState } from 'react';
import { useUserStore } from '@/lib/store/userStore';
import { api } from '@/lib/api/client';
import { DasibomLogo } from '@/components/icons/DasibomLogo';
import { Conversation } from '@/components/icons/Conversation';
import { SafeHaven } from '@/components/icons/SafeHaven';

const STEPS = [
  {
    icon: <DasibomLogo width={40} height={40} color="var(--L-point)" />,
    title: '다시봄에 오신 걸 환영해요',
    body: '싸우거나 서운한 일이 있을 때, 내 마음을 먼저 정리하는 도구예요. AI 중재자가 판단 없이 이야기를 들어드려요.',
  },
  {
    icon: <Conversation width={40} height={40} color="var(--L-point)" />,
    title: '각자 대화해요',
    body: '상대방과 직접 대화하는 게 아니에요. 두 분이 각자 중재자와 이야기하면, 서로의 마음을 더 잘 이해할 수 있어요.',
  },
  {
    icon: <SafeHaven width={40} height={40} color="var(--L-point)" />,
    title: '안전하게 보관돼요',
    body: '나눈 이야기는 30일 후 자동으로 사라져요. 누가 맞고 틀렸는지 가리지 않아요. 서로를 조금 더 이해하는 게 목표예요.',
  },
] as const;

export function OnboardingModal() {
  const user = useUserStore((s) => s.user);
  const setTutorialCompleted = useUserStore((s) => s.setTutorialCompleted);
  const [step, setStep] = useState(0);
  const [completing, setCompleting] = useState(false);

  const shouldShow =
    !!user && !user.isGuest && user.tutorialCompleted === false;

  if (!shouldShow) return null;

  const current = STEPS[step];
  const isLast = step === STEPS.length - 1;

  const handleNext = () => {
    if (isLast) {
      handleComplete();
    } else {
      setStep((s) => s + 1);
    }
  };

  const handleComplete = async () => {
    setCompleting(true);
    try {
      await api.post('/api/users/me/tutorial/complete');
    } catch {
      // 실패해도 로컬 상태는 완료로 처리 (재노출 방지)
    } finally {
      setTutorialCompleted();
      setCompleting(false);
    }
  };

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0, 0, 0, 0.45)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 8000,
        padding: '0 24px',
      }}
    >
      <div
        style={{
          background: 'var(--L-bg)',
          borderRadius: 16,
          padding: '36px 28px 28px',
          maxWidth: 340,
          width: '100%',
          boxShadow: '0 4px 24px rgba(0,0,0,0.12)',
        }}
      >
        {/* 아이콘 */}
        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 20 }}>
          {current.icon}
        </div>

        {/* 제목 */}
        <div
          className="serif"
          style={{
            fontSize: 19,
            fontWeight: 500,
            color: 'var(--L-ink)',
            textAlign: 'center',
            marginBottom: 12,
            lineHeight: 1.4,
          }}
        >
          {current.title}
        </div>

        {/* 본문 */}
        <div
          style={{
            fontSize: 14,
            color: 'var(--L-sub)',
            lineHeight: 1.75,
            textAlign: 'center',
            marginBottom: 28,
          }}
        >
          {current.body}
        </div>

        {/* Dot indicator */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            gap: 6,
            marginBottom: 24,
          }}
        >
          {STEPS.map((_, i) => (
            <div
              key={i}
              style={{
                width: i === step ? 18 : 6,
                height: 6,
                borderRadius: 3,
                background: i === step ? 'var(--L-point)' : 'var(--L-rule)',
                transition: 'width 0.25s ease, background 0.25s ease',
              }}
            />
          ))}
        </div>

        {/* 버튼 */}
        <button
          onClick={handleNext}
          disabled={completing}
          className="btn-L"
          style={{ width: '100%', textAlign: 'center' }}
        >
          {isLast ? (completing ? '…' : '시작하기') : '다음'}
        </button>
      </div>
    </div>
  );
}
