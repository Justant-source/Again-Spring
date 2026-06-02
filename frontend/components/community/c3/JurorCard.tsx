'use client';

interface JurorCardProps {
  name: string;
  lens: string;
  text: string;
  accent?: string;
}

export function JurorCard({
  name,
  lens,
  text,
  accent = 'var(--P-sub)',
}: JurorCardProps) {
  return (
    <div
      style={{
        background: 'var(--P-card)',
        border: '1px solid var(--P-border)',
        borderRadius: 14,
        padding: '14px 16px',
      }}
    >
      {/* 상단: 색 원 + AI 라벨 + 이름 + 렌즈 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        <div
          style={{
            width: 24,
            height: 24,
            borderRadius: '50%',
            background: accent,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'white',
            fontSize: 11,
            fontWeight: 700,
            flexShrink: 0,
          }}
        >
          AI
        </div>
        <div style={{ flex: 1 }}>
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: 'var(--P-ink)',
            }}
          >
            {name}
          </div>
          <div
            style={{
              fontSize: 11,
              color: 'var(--P-sub)',
            }}
          >
            · {lens}
          </div>
        </div>
      </div>

      {/* 본문 */}
      <p
        style={{
          margin: 0,
          fontSize: 12,
          fontFamily: 'var(--font-serif)',
          lineHeight: 1.6,
          color: 'var(--P-ink)',
        }}
      >
        {text}
      </p>
    </div>
  );
}
