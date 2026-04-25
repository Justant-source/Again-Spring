'use client';

import { useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { PhoneFrame, PhoneHeader } from '@/components/shared/PhoneFrame';
import { Dashes } from '@/components/shared/Dashes';
import { MBTI_TEST_QUESTIONS, MBTI_TO_STYLE, deriveMbtiType } from '@/lib/constants/mbtiMapping';
import { useUserStore } from '@/lib/store/userStore';

const TOTAL = MBTI_TEST_QUESTIONS.length;

export default function MbtiTestPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const setStyle = useUserStore((s) => s.setStyle);
  const setMbtiType = useUserStore((s) => s.setMbtiType);

  const nextPath = searchParams.get('next') ?? '/session/new';

  const [currentIdx, setCurrentIdx] = useState(0);
  const [answers, setAnswers] = useState<Record<string, string>>({});

  const question = MBTI_TEST_QUESTIONS[currentIdx];
  const answered = Object.keys(answers).length;

  const handleChoice = (letter: string) => {
    const newAnswers = { ...answers, [question.id]: letter };
    setAnswers(newAnswers);

    if (currentIdx < TOTAL - 1) {
      setTimeout(() => setCurrentIdx((i) => i + 1), 280);
      return;
    }

    // Last question — derive MBTI and complete
    const mbtiType = deriveMbtiType(newAnswers);
    const style = MBTI_TO_STYLE[mbtiType] ?? 'leaf';
    setStyle(style);
    setMbtiType(mbtiType);
    router.push(`/onboarding/result?next=${encodeURIComponent(nextPath)}`);
  };

  const handleBack = () => {
    if (currentIdx > 0) {
      setCurrentIdx((i) => i - 1);
    } else {
      router.back();
    }
  };

  const currentAnswer = answers[question.id];

  // Dimension label shown above question
  const DIM_LABEL: Record<string, string> = {
    EI: '외향 · 내향',
    SN: '감각 · 직관',
    TF: '사고 · 감정',
    JP: '판단 · 인식',
  };

  return (
    <PhoneFrame tone="L">
      <PhoneHeader title="MBTI 간이 검사" back={true} onBack={handleBack} />
      <div style={{ padding: '8px 28px 32px', flex: 1, display: 'flex', flexDirection: 'column' }}>

        {/* Progress */}
        <div style={{ marginBottom: 28 }}>
          <Dashes
            n={TOTAL}
            done={answered}
            current={currentIdx}
            onDashClick={(idx) => {
              if (idx <= answered) setCurrentIdx(idx);
            }}
          />
          <div style={{ marginTop: 6, fontSize: 11, color: 'var(--L-sub)' }}>
            {currentIdx + 1} / {TOTAL}
          </div>
        </div>

        {/* Dimension badge */}
        <div
          style={{
            display: 'inline-block',
            alignSelf: 'flex-start',
            fontSize: 11,
            color: 'var(--L-accent)',
            background: 'color-mix(in srgb, var(--L-accent) 10%, transparent)',
            borderRadius: 6,
            padding: '3px 9px',
            marginBottom: 16,
          }}
        >
          {DIM_LABEL[question.dimension]}
        </div>

        {/* Question */}
        <div
          className="serif"
          style={{
            fontSize: 20,
            lineHeight: 1.55,
            color: 'var(--L-ink)',
            marginBottom: 36,
          }}
        >
          {question.text}
        </div>

        {/* Options */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {[question.optionA, question.optionB].map((opt) => {
            const isSelected = currentAnswer === opt.letter;
            return (
              <button
                key={opt.letter}
                onClick={() => handleChoice(opt.letter)}
                style={{
                  width: '100%',
                  padding: '18px 20px',
                  borderRadius: 12,
                  border: `1.5px solid ${isSelected ? 'var(--L-accent)' : 'var(--L-rule)'}`,
                  background: isSelected ? 'var(--L-accent)' : 'var(--L-bg)',
                  color: isSelected ? '#fff' : 'var(--L-ink)',
                  textAlign: 'left',
                  fontSize: 15,
                  lineHeight: 1.4,
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                }}
              >
                <span
                  style={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    border: `1.5px solid ${isSelected ? 'rgba(255,255,255,0.5)' : 'var(--L-rule)'}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 12,
                    fontWeight: 700,
                    flexShrink: 0,
                    color: isSelected ? '#fff' : 'var(--L-sub)',
                  }}
                >
                  {opt.letter}
                </span>
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>
    </PhoneFrame>
  );
}
