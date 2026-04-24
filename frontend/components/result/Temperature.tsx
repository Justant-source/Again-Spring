// ✅ MOCKUP APPLIED — source: design/handoff/tone-P-screens.jsx (ReportCards)
'use client';

interface TemperatureProps {
  value: number | null;
  deltaFromPrev?: number;
}

export function Temperature({ value, deltaFromPrev }: TemperatureProps) {
  const getInterpretation = (v: number) => {
    if (v < 35.5) return '회복에 시간이 필요해 보여요. 전문가의 도움도 도움이 될 수 있어요.';
    if (v < 36.5) return '살짝 내려가 있지만, 회복의 범위 안에 있어요.';
    if (v < 37.0) return '따뜻하게 머무르고 있어요.';
    return '충만한 대화가 오갔어요.';
  };

  const gaugeWidth = value !== null ? ((value - 35) / (37.5 - 35)) * 100 : 0;

  return (
    <div style={{ textAlign: 'center', padding: 24 }}>
      <div style={{ fontSize: 12, color: 'var(--P-sub)', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
          <path d="M10 14.5V5a2 2 0 0 1 4 0v9.5" />
          <circle cx="12" cy="17" r="3" />
          <line x1="12" y1="8" x2="12" y2="14" />
        </svg>
        관계 온도
      </div>

      {value !== null ? (
        <>
          <div
            style={{
              fontSize: 56,
              fontWeight: 500,
              fontFamily: 'var(--font-serif)',
              letterSpacing: '-0.03em',
              marginTop: 6,
            }}
          >
            {value.toFixed(1)}
            <span style={{ fontSize: 24 }}>°C</span>
          </div>

          {deltaFromPrev !== undefined && deltaFromPrev !== 0 && (
            <div style={{ fontSize: 12, color: 'var(--P-sub)', marginTop: 4 }}>
              {deltaFromPrev > 0 ? '↑' : '↓'} {Math.abs(deltaFromPrev).toFixed(1)}°
            </div>
          )}

          <div style={{ marginTop: 10, position: 'relative', height: 6, background: 'var(--P-bg)', borderRadius: 4 }}>
            <div
              style={{
                position: 'absolute',
                left: 0,
                top: 0,
                bottom: 0,
                width: `${Math.min(100, Math.max(0, gaugeWidth))}%`,
                background: 'linear-gradient(90deg, var(--P-b), var(--P-a))',
                borderRadius: 4,
              }}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: 'var(--P-sub)', marginTop: 6 }}>
            <span>35.0</span>
            <span>평균 36.5</span>
            <span>37.5</span>
          </div>

          <div style={{ marginTop: 14, fontSize: 13, color: 'var(--P-ink)', lineHeight: 1.6 }}>
            {getInterpretation(value)}
          </div>
        </>
      ) : (
        <>
          <div style={{ fontSize: 18, fontWeight: 500, marginTop: 12, color: 'var(--P-ink)' }}>측정 중</div>
          <div style={{ marginTop: 10, position: 'relative', height: 6, background: 'var(--P-bg)', borderRadius: 4 }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10, color: 'var(--P-sub)', marginTop: 6 }}>
            <span>35.0</span>
            <span>평균 36.5</span>
            <span>37.5</span>
          </div>
          <div style={{ marginTop: 14, fontSize: 13, color: 'var(--P-ink)', lineHeight: 1.6 }}>
            두 분이 함께 하실 때 측정돼요.
          </div>
        </>
      )}
    </div>
  );
}
