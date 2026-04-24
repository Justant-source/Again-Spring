// ✅ MOCKUP APPLIED — source: design/handoff/tone-L-screens.jsx (OnboardingSlider)
'use client';

import { useEffect } from 'react';
import type { OnboardingQuestion } from '@/lib/constants/onboardingQuestions';

interface LikertQuestionProps {
  question: OnboardingQuestion;
  value: number | null;
  onChange: (value: number) => void;
}

export function LikertQuestion({ question, value, onChange }: LikertQuestionProps) {
  // Handle keyboard navigation: ← → to cycle through 1-5
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowLeft') {
        e.preventDefault();
        if (value === null) onChange(1);
        else if (value > 1) onChange(value - 1);
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        if (value === null) onChange(5);
        else if (value < 5) onChange(value + 1);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [value, onChange]);

  return (
    <div>
      <div className="quote-it" style={{ fontSize: 13, marginBottom: 14 }}>
        {question.id.toUpperCase()}
      </div>
      <div className="serif" style={{ fontSize: 20, lineHeight: 1.6, marginBottom: 44, minHeight: 120 }}>
        {question.text}
      </div>

      <div>
        <div className="likert">
          {[1, 2, 3, 4, 5].map((n) => (
            <button
              key={n}
              type="button"
              className={'likert-dot' + (n === value ? ' on' : '')}
              onClick={() => onChange(n)}
              aria-label={`${n}번 선택`}
              title={`${n}`}
            >
              {n}
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 10, fontSize: 11, color: 'var(--L-sub)' }}>
          <span>전혀 아니다</span>
          <span>매우 그렇다</span>
        </div>
      </div>
    </div>
  );
}
