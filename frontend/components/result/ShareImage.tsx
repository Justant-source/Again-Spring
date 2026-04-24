// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ShareImage)
'use client';

import React from 'react';
import { NeedsMap } from './NeedsMap';
import { Temperature } from './Temperature';
import { StyleCombination } from './StyleCombination';
import type { Report, CommunicationStyle } from '@/lib/types';

interface ShareImageProps {
  variant: 'map' | 'temp' | 'style';
  report: Report;
  styleA?: CommunicationStyle;
  styleB?: CommunicationStyle;
  nameA?: string;
  nameB?: string;
}

export function ShareImage({
  variant = 'map',
  report,
  styleA,
  styleB,
  nameA = '서현',
  nameB = '준호',
}: ShareImageProps) {
  const W = 270;
  const H = 480;

  const baseStyle = {
    width: W,
    height: H,
    borderRadius: 18,
    padding: '36px 28px',
    position: 'relative' as const,
    border: '1px solid var(--P-border)',
    display: 'flex' as const,
    flexDirection: 'column' as const,
    fontFamily: 'var(--font-sans)',
    color: 'var(--P-ink)',
  };

  if (variant === 'map') {
    return (
      <div style={{ ...baseStyle, background: 'var(--P-bg)' }}>
        <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄 · 부부</div>
        <div className="serif" style={{ fontSize: 20, marginTop: 8, lineHeight: 1.4 }}>
          우리의
          <br />
          마음 풍경
        </div>
        <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <NeedsMap
            positionA={report.needsMap.positionA}
            positionB={report.needsMap.positionB}
            axisX={report.needsMap.axisX}
            axisY={report.needsMap.axisY}
            labelA={nameA}
            labelB={nameB}
            size={200}
          />
        </div>
        <div style={{ fontSize: 11, color: 'var(--P-sub)', textAlign: 'center' }}>again-spring.com</div>
      </div>
    );
  }

  if (variant === 'temp') {
    return (
      <div style={{ ...baseStyle, background: 'var(--P-card)', textAlign: 'center', padding: '48px 28px' }}>
        <div style={{ fontSize: 11, color: 'var(--P-sub)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
            <path d="M10 14.5V5a2 2 0 0 1 4 0v9.5" />
            <circle cx="12" cy="17" r="3" />
            <line x1="12" y1="8" x2="12" y2="14" />
          </svg>
          관계 온도
        </div>
        <div className="serif" style={{ fontSize: 96, fontWeight: 500, lineHeight: 1, letterSpacing: '-0.04em', marginTop: 30 }}>
          {report.temperature ? report.temperature.toFixed(1) : '—'}°
        </div>
        <div style={{ marginTop: 18, width: '100%', height: 6, background: 'var(--P-bg)', borderRadius: 4 }}>
          {report.temperature && (
            <div
              style={{
                width: `${((report.temperature - 35) / (37.5 - 35)) * 100}%`,
                height: '100%',
                background: 'linear-gradient(90deg, var(--P-b), var(--P-a))',
                borderRadius: 4,
              }}
            />
          )}
        </div>
        <div style={{ marginTop: 30, fontFamily: 'var(--font-serif)', fontSize: 14, lineHeight: 1.7 }}>
          {report.temperature ? (
            report.temperature < 35.5
              ? '회복에 시간이 필요해 보여요.'
              : report.temperature < 36.5
                ? '살짝 내려가 있지만,<br/>회복의 범위 안에 있어요.'
                : report.temperature < 37.0
                  ? '따뜻하게 머무르고 있어요.'
                  : '충만한 대화가 오갔어요.'
          ) : (
            '두 분이 함께 할 때 측정돼요.'
          )}
        </div>
        <div style={{ flex: 1 }} />
        <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄 · again-spring.com</div>
      </div>
    );
  }

  // style variant
  return (
    <div style={{ ...baseStyle, background: 'var(--P-bg)', textAlign: 'center' }}>
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>우리의 대화 스타일</div>

      <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <StyleCombination styleA={styleA} styleB={styleB} nameA={nameA} nameB={nameB} />
      </div>

      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄 · again-spring.com</div>
    </div>
  );
}

/**
 * Renders a static DOM node for future canvas capture.
 * Returns the HTML string representation of the ShareImage variant.
 */
export function renderShareImageHTML(
  variant: 'map' | 'temp' | 'style',
  report: Report,
  styleA?: CommunicationStyle,
  styleB?: CommunicationStyle,
  nameA = '서현',
  nameB = '준호',
): string {
  // Stub for now — actual canvas capture will be handled by a screenshot utility
  return `<!-- Share Image: ${variant} -->`;
}
