'use client';

import type { Report } from '@/lib/types';

interface Props {
  report: Report;
}

const LABELS = ['비난', '경멸', '방어', '담쌓기'] as const;

export function ShareCardHorsemen({ report }: Props) {
  const obs = report.horsemenObservation;
  const scores = obs
    ? [obs.criticism.score, obs.contempt.score, obs.defensiveness.score, obs.stonewalling.score]
    : [0, 0, 0, 0];

  return (
    <div
      style={{
        width: 270,
        height: 480,
        background: 'var(--P-bg)',
        border: '1px solid var(--P-border)',
        borderRadius: 18,
        padding: '36px 28px',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'var(--font-sans)',
        color: 'var(--P-ink)',
      }}
    >
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄</div>
      <div className="serif" style={{ fontSize: 20, marginTop: 14, lineHeight: 1.4 }}>
        내가 자주 쓴<br />표현들
      </div>

      <div style={{ marginTop: 28, display: 'flex', flexDirection: 'column', gap: 16 }}>
        {LABELS.map((label, i) => {
          const score = scores[i];
          const pct = Math.min(100, (score / 10) * 100);
          return (
            <div key={label}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 6 }}>
                <span>{label}</span>
                <span style={{ color: 'var(--P-sub)' }}>{score}</span>
              </div>
              <div
                style={{
                  height: 6,
                  background: 'var(--P-card)',
                  borderRadius: 3,
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    width: `${pct}%`,
                    height: '100%',
                    background: 'var(--P-sub)',
                    opacity: 0.4 + (score / 10) * 0.5,
                  }}
                />
              </div>
            </div>
          );
        })}
      </div>

      <div style={{ marginTop: 'auto', fontSize: 11, color: 'var(--P-sub)', textAlign: 'center', lineHeight: 1.6 }}>
        이 표현들은 평범한 대화에서도<br />나타나요. 안심해도 괜찮아요.
      </div>
      <div style={{ fontSize: 10, color: 'var(--P-sub)', textAlign: 'center', marginTop: 8 }}>
        이 결과는 다시봄의 참고용 분석이에요 · againspring.net
      </div>
    </div>
  );
}
