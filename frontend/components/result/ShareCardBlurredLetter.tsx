'use client';

import type { Report } from '@/lib/types';

interface Props {
  report: Report;
  nameA?: string;
}

export function ShareCardBlurredLetter({ report, nameA = '서현' }: Props) {
  const sentences = [
    report.nvcScripts?.aToB?.observation ?? '',
    report.nvcScripts?.aToB?.feeling ?? '',
    report.nvcScripts?.aToB?.need ?? '',
    report.nvcScripts?.aToB?.request ?? '',
  ];

  return (
    <div
      style={{
        width: 270,
        height: 480,
        background: 'var(--P-bg)',
        border: '1px solid var(--P-border)',
        borderRadius: 18,
        padding: '36px 28px',
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'var(--font-sans)',
        color: 'var(--P-ink)',
      }}
    >
      <div style={{ fontSize: 11, color: 'var(--P-sub)' }}>다시봄</div>
      <div className="serif" style={{ fontSize: 20, marginTop: 12, lineHeight: 1.4 }}>
        보내려고 했는데<br />못한 4문장
      </div>
      <div style={{ fontSize: 11, color: 'var(--P-sub)', marginTop: 6 }}>
        관찰 · 감정 · 욕구 · 부탁
      </div>

      <div
        style={{
          marginTop: 18,
          padding: '18px 16px',
          background: 'var(--P-card)',
          border: '1px solid var(--P-border)',
          borderRadius: 12,
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
          justifyContent: 'center',
        }}
      >
        {sentences.map((s, i) => (
          <div
            key={i}
            className="serif"
            style={{
              fontSize: 13,
              lineHeight: 1.6,
              filter: 'blur(4px)',
              userSelect: 'none',
            }}
          >
            {s.length > 0 ? s : '...'}
          </div>
        ))}
      </div>

      <div style={{ marginTop: 14, fontSize: 11, color: 'var(--P-sub)', textAlign: 'center' }}>
        전체 보기는 다시봄에서
      </div>
      <div style={{ fontSize: 11, color: 'var(--P-sub)', textAlign: 'center', marginTop: 4 }}>
        againspring.net
      </div>
    </div>
  );
}
