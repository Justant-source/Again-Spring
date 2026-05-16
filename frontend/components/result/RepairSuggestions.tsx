'use client';

interface RepairSuggestionsProps {
  suggestions: string[];
}

export function RepairSuggestions({ suggestions }: RepairSuggestionsProps) {
  return (
    <div>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', marginBottom: 10 }}>오늘 건넬 수 있는 말</div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {suggestions.map((suggestion, i) => (
          <div key={i}>
            <div
              style={{
                padding: '12px 14px',
                border: '1px dashed var(--P-border)',
                borderRadius: 10,
                fontFamily: 'var(--font-serif)',
                fontSize: 14,
                lineHeight: 1.7,
                color: 'var(--P-ink)',
              }}
            >
              "{suggestion}"
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
