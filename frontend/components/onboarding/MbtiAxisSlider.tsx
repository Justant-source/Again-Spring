'use client';

/**
 * MBTI 단일 축 슬라이더. MediatorStylePicker와 동일한 톤(L) 디자인 언어 사용.
 * - 양 끝 글자(E/I 등)는 활성화 시 진한 잉크색, 비활성 시 약한 보조색
 * - accentColor를 명시해 브라우저 기본 초록 제거
 */
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
  const isCenter = value === 50;

  return (
    <div style={{ width: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          marginBottom: 10,
        }}
      >
        <span style={{ fontSize: 11, color: 'var(--L-sub)', letterSpacing: 0.5 }}>
          {axisLabel}
        </span>
        <span style={{ fontSize: 11, color: 'var(--L-sub)' }}>
          {isCenter ? '균형' : isLeft ? `${leftLetter} ${leftPct}` : `${rightLetter} ${rightPct}`}
        </span>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <span
          className="serif"
          style={{
            fontSize: 18,
            fontWeight: 600,
            color: !isCenter && isLeft ? 'var(--L-ink)' : 'var(--L-sub)',
            opacity: !isCenter && isLeft ? 1 : 0.4,
            minWidth: 18,
            textAlign: 'center',
            transition: 'color 0.2s, opacity 0.2s',
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
          aria-label={`${axisLabel}: ${leftLetter} ↔ ${rightLetter}`}
          style={{
            flex: 1,
            height: 4,
            accentColor: 'var(--L-ink)',
            cursor: 'pointer',
          }}
        />
        <span
          className="serif"
          style={{
            fontSize: 18,
            fontWeight: 600,
            color: !isCenter && !isLeft ? 'var(--L-ink)' : 'var(--L-sub)',
            opacity: !isCenter && !isLeft ? 1 : 0.4,
            minWidth: 18,
            textAlign: 'center',
            transition: 'color 0.2s, opacity 0.2s',
          }}
        >
          {rightLetter}
        </span>
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          marginTop: 6,
          fontSize: 11,
        }}
      >
        <span style={{ color: !isCenter && isLeft ? 'var(--L-ink)' : 'var(--L-sub)' }}>
          {leftLabel}
        </span>
        <span style={{ color: !isCenter && !isLeft ? 'var(--L-ink)' : 'var(--L-sub)' }}>
          {rightLabel}
        </span>
      </div>
    </div>
  );
}
