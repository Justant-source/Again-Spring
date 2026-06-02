'use client';

import { useState } from 'react';

interface JurorPickerProps {
  onChange: (value: number) => void;
  defaultValue?: number;
}

export function JurorPicker({ onChange, defaultValue = 3 }: JurorPickerProps) {
  const [count, setCount] = useState(defaultValue);

  const handleChange = (delta: number) => {
    const newCount = Math.max(0, Math.min(9, count + delta));
    setCount(newCount);
    onChange(newCount);
  };

  const label = count === 0 ? 'AI 배심원 없이' : `AI 배심원 ${count}명`;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '12px 0',
      }}
    >
      <span style={{ fontSize: 13, color: 'var(--L-ink)' }}>{label}</span>

      {/* 스테퍼 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <button
          type="button"
          onClick={() => handleChange(-1)}
          disabled={count === 0}
          style={{
            width: 26,
            height: 26,
            borderRadius: '50%',
            border: `1px solid var(--grn)`,
            background: 'white',
            color: 'var(--grn)',
            cursor: count === 0 ? 'not-allowed' : 'pointer',
            fontSize: 14,
            fontWeight: 600,
            opacity: count === 0 ? 0.4 : 1,
            transition: 'opacity 0.15s',
          }}
        >
          −
        </button>

        <span
          style={{
            fontSize: 13,
            fontWeight: 600,
            color: 'var(--L-ink)',
            minWidth: 20,
            textAlign: 'center',
          }}
        >
          {count}
        </span>

        <button
          type="button"
          onClick={() => handleChange(1)}
          disabled={count === 9}
          style={{
            width: 26,
            height: 26,
            borderRadius: '50%',
            border: `1px solid var(--grn)`,
            background: 'white',
            color: 'var(--grn)',
            cursor: count === 9 ? 'not-allowed' : 'pointer',
            fontSize: 14,
            fontWeight: 600,
            opacity: count === 9 ? 0.4 : 1,
            transition: 'opacity 0.15s',
          }}
        >
          +
        </button>
      </div>
    </div>
  );
}
