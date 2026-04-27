'use client';

export interface MbtiAxisSliderProps {
  axisLabel: string;
  leftLetter: string;
  leftLabel: string;
  rightLetter: string;
  rightLabel: string;
  value: number; // 0=fully left, 100=fully right
  onChange: (value: number) => void;
}

export function MbtiAxisSlider({
  axisLabel,
  leftLetter,
  leftLabel,
  rightLetter,
  rightLabel,
  value,
  onChange,
}: MbtiAxisSliderProps) {
  const leftPct = 100 - value;
  const rightPct = value;
  const isLeft = value < 50;

  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--L-sub)', marginBottom: 10 }}>{axisLabel}</div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <span
          style={{
            fontSize: 17,
            fontWeight: 700,
            color: isLeft ? 'var(--L-accent)' : 'var(--L-rule)',
            minWidth: 22,
            transition: 'color 0.2s',
          }}
        >
          {leftLetter}
        </span>
        <input
          type="range"
          min={0}
          max={100}
          step={5}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          style={{
            flex: 1,
            height: 4,
            accentColor: 'var(--L-accent)',
            cursor: 'pointer',
          }}
        />
        <span
          style={{
            fontSize: 17,
            fontWeight: 700,
            color: !isLeft ? 'var(--L-accent)' : 'var(--L-rule)',
            minWidth: 22,
            textAlign: 'right',
            transition: 'color 0.2s',
          }}
        >
          {rightLetter}
        </span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 5 }}>
        <span style={{ fontSize: 11, color: isLeft ? 'var(--L-accent)' : 'var(--L-sub)' }}>
          <span>{leftLabel}</span>{' '}<span>{leftPct}%</span>
        </span>
        <span style={{ fontSize: 11, color: !isLeft ? 'var(--L-accent)' : 'var(--L-sub)' }}>
          <span>{rightLabel}</span>{' '}<span>{rightPct}%</span>
        </span>
      </div>
    </div>
  );
}
