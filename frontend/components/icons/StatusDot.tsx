import type { SVGProps } from 'react';

type DistanceLevel = 1 | 2 | 3 | 4 | 5;

const LEVEL_COLORS: Record<DistanceLevel, string> = {
  1: '#4ADE80',
  2: '#86EFAC',
  3: '#FCD34D',
  4: '#FB923C',
  5: '#F87171',
};

interface StatusDotProps extends SVGProps<SVGSVGElement> {
  level: DistanceLevel;
  size?: number;
}

export function StatusDot({ level, size = 12, ...props }: StatusDotProps) {
  const color = LEVEL_COLORS[level];
  return (
    <svg
      viewBox="0 0 12 12"
      width={size}
      height={size}
      aria-hidden="true"
      style={{ display: 'inline-block', verticalAlign: 'middle', flexShrink: 0 }}
      {...props}
    >
      <circle cx={6} cy={6} r={5} fill={color} />
    </svg>
  );
}
