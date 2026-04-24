import type { CSSProperties } from 'react';

export function Logo({
  size = 14,
  color = 'var(--L-ink)',
  className,
  style,
}: {
  size?: number;
  color?: string;
  className?: string;
  style?: CSSProperties;
}) {
  return (
    <span
      className={className}
      style={{
        fontFamily: 'var(--font-serif)',
        fontSize: size,
        color,
        letterSpacing: '-0.02em',
        fontWeight: 500,
        ...style,
      }}
    >
      다시봄
    </span>
  );
}
