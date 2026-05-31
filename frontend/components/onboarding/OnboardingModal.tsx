'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
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

// 설명 카드 3개 + 마지막에 '대화 스타일 설정(선택)' 안내 1개 = 가입 직후 1회만 노출
const TOTAL = STEPS.length + 1;

export function OnboardingModal() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const setTutorialCompleted = useUserStore((s) => s.setTutorialCompleted);
  const [step, setStep] = useState(0);
  const [completing, setCompleting] = useState(false);

  const shouldShow =
    !!user && !user.isGuest && user.tutorialCompleted === false;

  if (!shouldShow) return null;

  const isStyleStep = step === STEPS.length; // 마지막 단계 = 스타일 설정 안내(선택)
  const current = isStyleStep ? null : STEPS[step];

  // 튜토리얼 완료 마킹 — tutorial_completed_at 으로 재노출 방지
  const markTutorialDone = async () => {
    try {
      await api.post('/api/users/me/tutorial/complete');
    } catch {
      // 실패해도 로컬 상태는 완료로 처리 (재노출 방지)
    } finally {
      setTutorialCompleted();
    }
  };

  const handleNext = () => setStep((s) => s + 1);

  // [지금 설정하기] — 완료 마킹 후 선택 온보딩(10문항/MBTI)으로 이동
  const handleSetupStyle = async () => {
    setCompleting(true);
    await markTutorialDone();
    setCompleting(false);
    router.push('/onboarding/intro?next=/session/new');
  };

  // [건너뛰고 시작하기] — 완료 마킹 후 모달만 닫힘(홈 유지, 바로 대화 시작 가능)
  const handleSkip = async () => {
    setCompleting(true);
    await markTutorialDone();
    setCompleting(false);
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
          {isStyleStep ? (
            <Conversation width={40} height={40} color="var(--L-point)" />
          ) : (
            current?.icon
          )}
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
          {isStyleStep ? '대화 스타일을 설정해볼까요?' : current?.title}
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
          {isStyleStep
            ? '약 1~2분이면 끝나요. 지금 건너뛰고 바로 시작해도 좋아요. 나중에 프로필에서 언제든 설정할 수 있어요.'
            : current?.body}
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
          {Array.from({ length: TOTAL }).map((_, i) => (
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
        {isStyleStep ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <button
              onClick={handleSetupStyle}
              disabled={completing}
              className="btn-L"
              style={{ width: '100%', textAlign: 'center' }}
            >
              {completing ? '…' : '지금 설정하기'}
            </button>
            <button
              onClick={handleSkip}
              disabled={completing}
              className="btn-L ghost"
              style={{ width: '100%', textAlign: 'center' }}
            >
              건너뛰고 시작하기
            </button>
          </div>
        ) : (
          <button
            onClick={handleNext}
            disabled={completing}
            className="btn-L"
            style={{ width: '100%', textAlign: 'center' }}
          >
            다음
          </button>
        )}
      </div>
    </div>
  );
}
