// ✅ MOCKUP APPLIED — source: design/handoff/mediation-screens.jsx (view variant toggle)

'use client';

export function ViewToggle({
  value,
  onChange,
}: {
  value: 'letter' | 'bubble' | 'card';
  onChange: (v: 'letter' | 'bubble' | 'card') => void;
}) {
  const options: Array<{ value: 'letter' | 'bubble' | 'card'; label: string }> =
    [
      { value: 'letter', label: '편지' },
      { value: 'bubble', label: '말풍선' },
      { value: 'card', label: '카드' },
    ];

  return (
    <div className="flex gap-1">
      {options.map((option) => (
        <button
          key={option.value}
          onClick={() => onChange(option.value)}
          className={
            value === option.value ? 'chip-L' : 'chip-L'
          }
          style={{
            fontSize: '12px',
            padding: '6px 10px',
            background:
              value === option.value
                ? 'var(--L-ink)'
                : 'transparent',
            color:
              value === option.value
                ? 'var(--L-bg)'
                : 'var(--L-ink)',
            border:
              value === option.value
                ? '1px solid var(--L-ink)'
                : '1px solid var(--L-border)',
            borderRadius: '999px',
            cursor: 'pointer',
            transition: 'all 0.15s',
          }}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
