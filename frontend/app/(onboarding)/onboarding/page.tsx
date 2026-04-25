// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (OnboardingSlider)
'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { Dashes } from '@/components/shared/Dashes';
import { LikertQuestion } from '@/components/onboarding/LikertQuestion';
import { ONBOARDING_QUESTIONS } from '@/lib/constants/onboardingQuestions';
import { useUserStore } from '@/lib/store/userStore';
import { determineStyle } from '@/lib/utils/styleCalculator';

export default function OnboardingPage() {
  const router = useRouter();
  const user = useUserStore((s) => s.user);
  const setOnboardingAnswers = useUserStore((s) => s.setOnboardingAnswers);
  const setStyle = useUserStore((s) => s.setStyle);

  const [currentIdx, setCurrentIdx] = useState(0);
  const [answers, setAnswers] = useState<(number | null)[]>([...Array(10)].map(() => null));
  const [loading, setLoading] = useState(false);

  // Hydrate from store on mount
  useEffect(() => {
    if (user?.onboardingAnswers) {
      setAnswers(user.onboardingAnswers);
    }
  }, [user?.onboardingAnswers]);

  const currentQuestion = ONBOARDING_QUESTIONS[currentIdx];
  const currentAnswer = answers[currentIdx];
  const isAnswered = currentAnswer !== null;
  const isLastQuestion = currentIdx === 9;
  const maxVisited = Math.max(
    currentIdx,
    answers.findLastIndex((a) => a !== null)
  );

  const submitAnswers = async (allAnswers: number[]) => {
    setLoading(true);
    try {
      setOnboardingAnswers(allAnswers);
      const style = determineStyle(allAnswers);
      setStyle(style);
      router.push('/onboarding/result');
    } finally {
      setLoading(false);
    }
  };

  const handleSelect = (val: number) => {
    const newAnswers = [...answers];
    newAnswers[currentIdx] = val;
    setAnswers(newAnswers);

    if (isLastQuestion) {
      if (newAnswers.every((a) => a !== null)) {
        submitAnswers(newAnswers as number[]);
      }
      return;
    }

    // Auto-advance after a short delay so user sees their selection
    setTimeout(() => {
      setCurrentIdx((idx) => (idx === currentIdx ? idx + 1 : idx));
    }, 250);
  };

  const handleNext = async () => {
    if (!isAnswered) return;

    if (isLastQuestion) {
      await submitAnswers(answers as number[]);
    } else {
      setCurrentIdx(currentIdx + 1);
    }
  };

  const handlePrev = () => {
    if (currentIdx > 0) {
      setCurrentIdx(currentIdx - 1);
    }
  };

  const handleJumpTo = (idx: number) => {
    if (idx <= maxVisited + 1 && idx < 10) {
      setCurrentIdx(idx);
    }
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="나의 대화 성향" back={false} />
      <div style={{ padding: '8px 28px 28px', flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div style={{ marginBottom: 28 }}>
          <Dashes
            n={10}
            done={answers.filter((a) => a !== null).length}
            current={currentIdx}
            onDashClick={handleJumpTo}
          />
          <div style={{ marginTop: 6, fontSize: 11, color: 'var(--L-sub)' }}>
            {currentIdx + 1} / 10
          </div>
        </div>

        <div style={{ flex: 1, marginBottom: 44 }}>
          <LikertQuestion
            question={currentQuestion}
            value={currentAnswer}
            onChange={handleSelect}
          />
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <button
            className="btn-L ghost"
            style={{ flex: 1 }}
            onClick={handlePrev}
            disabled={currentIdx === 0}
            aria-label="이전 질문"
          >
            이전
          </button>
          <button
            className="btn-L"
            style={{ flex: 2 }}
            onClick={handleNext}
            disabled={!isAnswered || loading}
            aria-label={isLastQuestion ? '완료' : '다음 질문'}
          >
            {loading ? '처리 중...' : isLastQuestion ? '완료' : '다음'}
          </button>
        </div>
      </div>
    </PhoneFrame>
  );
}
