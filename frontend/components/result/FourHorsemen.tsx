// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ReportCards)
'use client';

import { useState } from 'react';
import type { HorsemenDetection } from '@/lib/types';

interface FourHorsemenProps {
  detection: HorsemenDetection;
}

const LABELS = [
  { key: 'criticism', label: '비판보다는 관찰', positive: true },
  { key: 'defensiveness', label: '방어보다는 이해', positive: true },
  { key: 'contempt', label: '경멸은 보이지 않음', positive: true },
  { key: 'stonewalling', label: '담쌓기보다는 머무름', positive: true },
];

export function FourHorsemen({ detection }: FourHorsemenProps) {
  const [expandedKey, setExpandedKey] = useState<string | null>(null);

  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 14 }}>대화 패턴 살펴보기</div>

      {LABELS.map(({ key, label }) => {
        const item = detection[key as keyof HorsemenDetection];
        const isDetected = item && 'detected' in item && item.detected;
        const examples = item && 'examples' in item ? item.examples : undefined;

        // Invert: detected = 0.35 (bad), not detected = 0.85 (good)
        const barValue = isDetected ? 0.35 : 0.85;
        const hasExamples = examples && examples.length > 0;

        return (
          <div key={key}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 }}>
              <span style={{ fontSize: 13 }}>{label}</span>
              <span style={{ fontSize: 11, color: 'var(--P-sub)', textAlign: 'right', flex: 1, marginLeft: 8 }}>
                {isDetected ? '보여요' : '보이지 않아요'}
              </span>
            </div>
            <div style={{ height: 4, background: 'var(--P-bg)', borderRadius: 2, marginBottom: 10 }}>
              <div
                style={{
                  height: '100%',
                  width: `${barValue * 100}%`,
                  background: 'var(--P-a)',
                  opacity: 0.7,
                  borderRadius: 2,
                }}
              />
            </div>

            {hasExamples && (
              <div style={{ marginBottom: 10 }}>
                <button
                  onClick={() => setExpandedKey(expandedKey === key ? null : key)}
                  style={{
                    background: 'none',
                    border: 'none',
                    fontSize: 11,
                    color: 'var(--P-sub)',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                    padding: 0,
                  }}
                >
                  {expandedKey === key ? '예시 숨기기' : '예시 보기'}
                </button>
                {expandedKey === key && (
                  <div style={{ marginTop: 8, padding: '8px 12px', background: 'var(--P-bg)', borderRadius: 8, fontSize: 12 }}>
                    {examples.map((ex, i) => (
                      <div key={i} style={{ marginBottom: i < examples.length - 1 ? 4 : 0 }}>
                        · {ex}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
