// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ShareImage)
'use client';

import React from 'react';
import { NeedsMap } from './NeedsMap';
import { StyleCombination } from './StyleCombination';
import type { Report, CommunicationStyle } from '@/lib/types';

interface ShareImageProps {
  variant: 'map' | 'style';
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
  variant: 'map' | 'style',
  report: Report,
  styleA?: CommunicationStyle,
  styleB?: CommunicationStyle,
  nameA = '서현',
  nameB = '준호',
): string {
  // Stub for now — actual canvas capture will be handled by a screenshot utility
  return `<!-- Share Image: ${variant} -->`;
}
