'use client';

import type { HorsemenObservation } from '@/lib/types';

interface Props {
  horsemen: HorsemenObservation;
}

const LABELS: Record<keyof HorsemenObservation, string> = {
  criticism: '비난',
  contempt: '경멸',
  defensiveness: '방어',
  stonewalling: '담쌓기',
};

function ScoreBar({ score }: { score: number }) {
  if (score === 0) {
    return (
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>거의 없었어요</div>
    );
  }
  const pct = (score / 10) * 100;
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: 'var(--P-sub)', marginBottom: 3 }}>
        <span style={{ opacity: 0.7 }}>관찰 수준</span>
        <span>{score}</span>
      </div>
      <div style={{ height: 5, borderRadius: 3, background: 'color-mix(in srgb, var(--P-sub) 12%, transparent)', overflow: 'hidden' }}>
        <div
          style={{
            height: '100%',
            width: `${pct}%`,
            background: 'var(--P-sub)',
            opacity: 0.35 + (score / 10) * 0.55,
            borderRadius: 3,
            transition: 'width 0.4s ease',
          }}
        />
      </div>
    </div>
  );
}

export function FourHorsemenObservation({ horsemen }: Props) {
  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>대화 표현 관찰</div>

      <div
        style={{
          fontSize: 11,
          color: 'var(--P-sub)',
          lineHeight: 1.7,
          marginBottom: 16,
          padding: '10px 12px',
          background: 'color-mix(in srgb, var(--P-sub) 5%, transparent)',
          borderRadius: 8,
        }}
      >
        이 4가지는 관계 의사소통 연구에서 자주 관찰되는 표현 방식이에요.
        점수가 높다고 관계가 위험하다는 뜻은 아니에요.
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {(Object.keys(LABELS) as Array<keyof HorsemenObservation>).map((key) => {
          const item = horsemen[key];
          return (
            <div key={key}>
              <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--P-ink)', marginBottom: 6 }}>
                {LABELS[key]}
              </div>
              <ScoreBar score={item.score} />
            </div>
          );
        })}
      </div>
    </div>
  );
}
