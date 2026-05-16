interface Props {
  width?: number;
  height?: number;
  color?: string;
  className?: string;
}

export function CrisisResources({ width = 24, height = 24, color = 'currentColor', className }: Props) {
  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 24 24"
      fill="none"
      stroke={color}
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {/* Circle */}
      <circle cx="12" cy="12" r="9" />
      {/* Plus / cross */}
      <line x1="12" y1="8" x2="12" y2="16" />
      <line x1="8" y1="12" x2="16" y2="12" />
    </svg>
  );
}
