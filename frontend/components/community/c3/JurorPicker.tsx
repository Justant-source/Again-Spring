'use client';

import { useState } from 'react';

const AUTHOR_COLOR = '#C9785A';

interface JurorPickerProps {
  onChange: (value: number) => void;
  defaultValue?: number;
}

export function JurorPicker({ onChange, defaultValue = 3 }: JurorPickerProps) {
  const [count, setCount] = useState(defaultValue);

  const handleDec = (e: React.MouseEvent) => {
    e.stopPropagation();
    const next = Math.max(0, count - 1);
    setCount(next);
    onChange(next);
  };

  const handleInc = (e: React.MouseEvent) => {
    e.stopPropagation();
    const next = Math.min(9, count + 1);
    setCount(next);
    onChange(next);
  };

  const label = count === 0 ? 'AI 배심원 없이' : `AI 배심원 ${count}명`;

  return (
    <div style={{
      padding: '14px 16px',
      border: '1px solid var(--L-border)',
      borderRadius: 12,
      background: 'var(--L-card)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
    }}>
      <span style={{ fontSize: 13.5, color: 'var(--L-ink)', fontWeight: 500 }}>{label}</span>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <button
          type="button"
          onClick={handleDec}
          style={{
            width: 26, height: 26, borderRadius: '50%',
            border: `1px solid ${AUTHOR_COLOR}`, color: AUTHOR_COLOR,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 16, cursor: 'pointer', background: '#fff',
            opacity: count === 0 ? 0.4 : 1,
            fontFamily: 'inherit',
          }}
        >−</button>

        <span style={{ minWidth: 16, textAlign: 'center', fontSize: 17, fontWeight: 500, color: 'var(--L-ink)' }}>
          {count}
        </span>

        <button
          type="button"
          onClick={handleInc}
          style={{
            width: 26, height: 26, borderRadius: '50%',
            border: `1px solid ${AUTHOR_COLOR}`, color: AUTHOR_COLOR,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 16, cursor: 'pointer', background: '#fff',
            opacity: count === 9 ? 0.4 : 1,
            fontFamily: 'inherit',
          }}
        >+</button>
      </div>
    </div>
  );
}
