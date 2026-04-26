'use client';

import type { Report } from '@/lib/types';

interface Props {
  report: Report;
  nameA?: string;
  nameB?: string;
}

export function ShareCardRatio({ report, nameA = '서현', nameB = '준호' }: Props) {
  const ratio = report.contributionRatio;
  if (!ratio) return null;

  const isExtreme = ratio.a >= 80 || ratio.b >= 80;

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
      <div className="serif" style={{ fontSize: 20, marginTop: 14 }}>
        함께 다가가는<br />균형
      </div>

      <div style={{ marginTop: 28, fontSize: 12, color: 'var(--P-sub)' }}>두 분의 회복 시작점</div>

      <div style={{ display: 'flex', height: 60, borderRadius: 12, overflow: 'hidden', marginTop: 10 }}>
        <div
          style={{
            flex: ratio.a,
            background: 'var(--P-a)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#5C4030',
            fontWeight: 500,
            fontSize: 14,
          }}
        >
          <div>{nameA}</div>
          {!isExtreme && <div style={{ fontSize: 18 }}>{ratio.a}</div>}
        </div>
        <div
          style={{
            flex: ratio.b,
            background: 'var(--P-b)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#3F4F45',
            fontWeight: 500,
            fontSize: 14,
          }}
        >
          <div>{nameB}</div>
          {!isExtreme && <div style={{ fontSize: 18 }}>{ratio.b}</div>}
        </div>
      </div>

      <div style={{ marginTop: 16, fontSize: 12, lineHeight: 1.7 }}>
        <div><strong>{nameA}</strong> · {ratio.label?.a ?? ''}</div>
        <div style={{ marginTop: 4 }}><strong>{nameB}</strong> · {ratio.label?.b ?? ''}</div>
      </div>

      <div style={{ marginTop: 'auto', fontSize: 11, color: 'var(--P-sub)', textAlign: 'center' }}>
        {isExtreme
          ? '특수한 상황의 결과로, 일반화하기 어려워요'
          : '잘잘못이 아닌 노력의 양이에요'}
      </div>
      <div style={{ fontSize: 10, color: 'var(--P-sub)', textAlign: 'center', marginTop: 6 }}>
        이 결과는 다시봄의 참고용 분석이에요 · againspring.net
      </div>
    </div>
  );
}
