// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ReportCards)
'use client';

import type { ContributionRatio as ContributionRatioType } from '@/lib/types';

interface ContributionRatioProps {
  ratio: ContributionRatioType | null;
  nameA?: string;
  nameB?: string;
}

export function ContributionRatio({ ratio, nameA = '서현', nameB = '준호' }: ContributionRatioProps) {
  if (!ratio) {
    return null;
  }

  const aPercent = ratio.a;
  const bPercent = ratio.b;

  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>화해 기여도</div>

      <div style={{ display: 'flex', height: 44, borderRadius: 10, overflow: 'hidden' }}>
        <div
          style={{
            flex: aPercent,
            background: 'var(--P-a)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#5C4030',
            fontWeight: 500,
            fontSize: 14,
          }}
        >
          {nameA} · {aPercent}
        </div>
        <div
          style={{
            flex: bPercent,
            background: 'var(--P-b)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#3F4F45',
            fontWeight: 500,
            fontSize: 14,
          }}
        >
          {nameB} · {bPercent}
        </div>
      </div>

      <div style={{ marginTop: 14, fontSize: 13, lineHeight: 1.7 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
          <span style={{ color: 'var(--P-a)', fontWeight: 500, minWidth: 56 }}>{nameA}</span>
          <span>{ratio.label.a}</span>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', marginTop: 6 }}>
          <span style={{ color: '#6B9080', fontWeight: 500, minWidth: 56 }}>{nameB}</span>
          <span>{ratio.label.b}</span>
        </div>
      </div>
    </div>
  );
}
