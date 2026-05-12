'use client';

import type { SoloStageFlow } from '@/lib/types';

interface SoloStageFlowProps {
  stages: SoloStageFlow[];
}

const STAGE_COLORS = ['var(--P-a)', 'var(--P-card)', 'var(--P-card)', 'var(--P-card)'];

export function SoloStageFlowSection({ stages }: SoloStageFlowProps) {
  if (!stages || stages.length === 0) return null;

  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 14 }}>
        대화 흐름
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
        {stages.map((stage, i) => (
          <div key={stage.stage} style={{ display: 'flex', gap: 12 }}>
            {/* Timeline column */}
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                flexShrink: 0,
              }}
            >
              <div
                style={{
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  background: i === 0 ? 'var(--P-ink)' : 'var(--P-card)',
                  border: '1px solid var(--P-border)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: 11,
                  fontWeight: 600,
                  color: i === 0 ? 'var(--P-card)' : 'var(--P-sub)',
                  flexShrink: 0,
                }}
              >
                {stage.stage}
              </div>
              {i < stages.length - 1 && (
                <div
                  style={{
                    width: 1,
                    flex: 1,
                    minHeight: 20,
                    background: 'var(--P-border)',
                    margin: '4px 0',
                  }}
                />
              )}
            </div>

            {/* Content column */}
            <div style={{ paddingBottom: i < stages.length - 1 ? 20 : 0, flex: 1 }}>
              <div
                style={{
                  fontSize: 11,
                  fontWeight: 600,
                  color: 'var(--P-sub)',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                  marginBottom: 6,
                  paddingTop: 4,
                }}
              >
                {stage.stageName}
              </div>
              {stage.userQuote && (
                <div
                  style={{
                    padding: '10px 12px',
                    background: 'var(--P-a)',
                    borderRadius: 10,
                    fontSize: 13,
                    lineHeight: 1.7,
                    fontFamily: 'var(--font-serif)',
                    color: 'var(--P-ink)',
                    marginBottom: 8,
                  }}
                >
                  &ldquo;{stage.userQuote}&rdquo;
                </div>
              )}
              {stage.interpretation && (
                <div
                  style={{
                    fontSize: 12,
                    color: 'var(--P-sub)',
                    lineHeight: 1.65,
                  }}
                >
                  {stage.interpretation}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
