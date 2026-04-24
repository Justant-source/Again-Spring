// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ShareImage)
'use client';

import type { CommunicationStyle } from '@/lib/types';
import { COMMUNICATION_STYLES, STYLE_COMBINATION_INSIGHTS, getStyleCombinationKey } from '@/lib/constants/communicationStyles';
import { STYLE_MOTIF } from '@/components/shared/Motif';

interface StyleCombinationProps {
  styleA: CommunicationStyle | undefined;
  styleB: CommunicationStyle | undefined;
  nameA: string;
  nameB: string;
}

export function StyleCombination({ styleA, styleB, nameA, nameB }: StyleCombinationProps) {
  if (!styleA && !styleB) {
    return null;
  }

  if (!styleB) {
    // Only A present
    const styleDefA = COMMUNICATION_STYLES[styleA!];
    const MotifA = STYLE_MOTIF[styleA!];

    return (
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 16 }}>당신의 대화 스타일</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, justifyContent: 'center' }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: '50%',
              background: 'var(--P-a)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#5C4030',
            }}
          >
            <MotifA size={24} color="currentColor" />
          </div>
          <div style={{ textAlign: 'left' }}>
            <div style={{ fontFamily: 'var(--font-serif)', fontSize: 16, fontWeight: 500, color: 'var(--P-ink)' }}>
              {styleDefA.label}
            </div>
            <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>{nameA}</div>
          </div>
        </div>
      </div>
    );
  }

  if (!styleA) {
    // Only B present
    const styleDefB = COMMUNICATION_STYLES[styleB];
    const MotifB = STYLE_MOTIF[styleB];

    return (
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 16 }}>파트너의 대화 스타일</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, justifyContent: 'center' }}>
          <div
            style={{
              width: 48,
              height: 48,
              borderRadius: '50%',
              background: 'var(--P-b)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#3F4F45',
            }}
          >
            <MotifB size={24} color="currentColor" />
          </div>
          <div style={{ textAlign: 'left' }}>
            <div style={{ fontFamily: 'var(--font-serif)', fontSize: 16, fontWeight: 500, color: 'var(--P-ink)' }}>
              {styleDefB.label}
            </div>
            <div style={{ fontSize: 12, color: 'var(--P-sub)' }}>{nameB}</div>
          </div>
        </div>
      </div>
    );
  }

  // Both present
  const styleDefA = COMMUNICATION_STYLES[styleA];
  const styleDefB = COMMUNICATION_STYLES[styleB];
  const MotifA = STYLE_MOTIF[styleA];
  const MotifB = STYLE_MOTIF[styleB];

  const combinationKey = getStyleCombinationKey(styleA, styleB);
  const insight = STYLE_COMBINATION_INSIGHTS[combinationKey];
  const insight_text = insight
    ? insight.advice
    : `서로 다른 결을 가진 두 분이에요.`;

  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 16 }}>우리의 대화 스타일</div>

      <div style={{ display: 'flex', gap: 14, alignItems: 'center', justifyContent: 'center', marginBottom: 20 }}>
        {/* A */}
        <div>
          <div
            style={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'var(--P-a)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#5C4030',
            }}
          >
            <MotifA size={30} color="currentColor" />
          </div>
          <div style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontWeight: 500, marginTop: 8, color: 'var(--P-ink)' }}>
            {styleDefA.label}
          </div>
          <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>{nameA}</div>
        </div>

        {/* Separator */}
        <div style={{ fontFamily: 'var(--font-serif)', fontSize: 18, color: 'var(--P-sub)' }}>×</div>

        {/* B */}
        <div>
          <div
            style={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'var(--P-b)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#3F4F45',
            }}
          >
            <MotifB size={30} color="currentColor" />
          </div>
          <div style={{ fontFamily: 'var(--font-serif)', fontSize: 14, fontWeight: 500, marginTop: 8, color: 'var(--P-ink)' }}>
            {styleDefB.label}
          </div>
          <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>{nameB}</div>
        </div>
      </div>

      <div style={{ fontFamily: 'var(--font-serif)', fontSize: 13, lineHeight: 1.8, color: 'var(--P-ink)' }}>
        {insight_text}
      </div>
    </div>
  );
}
