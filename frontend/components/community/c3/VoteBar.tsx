'use client';

interface VoteBarProps {
  authorPct: number;
  big?: boolean;
}

export function VoteBar({ authorPct, big = false }: VoteBarProps) {
  const height = big ? 52 : 44;
  const partnerPct = 100 - authorPct;

  return (
    <div
      style={{
        display: 'flex',
        height: `${height}px`,
        borderRadius: 12,
        border: '1px solid var(--P-border)',
        overflow: 'hidden',
      }}
    >
      {/* 작성자(초록) */}
      <div
        style={{
          flex: authorPct,
          background: 'var(--grn)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          fontSize: 13,
          fontWeight: 600,
          minWidth: 0,
        }}
      >
        {authorPct > 10 && `${authorPct}%`}
      </div>

      {/* 상대방(붉은) */}
      <div
        style={{
          flex: partnerPct,
          background: 'var(--red)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'white',
          fontSize: 13,
          fontWeight: 600,
          minWidth: 0,
        }}
      >
        {partnerPct > 10 && `${partnerPct}%`}
      </div>
    </div>
  );
}
