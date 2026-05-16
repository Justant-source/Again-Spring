interface Props {
  width?: number;
  height?: number;
  color?: string;
  className?: string;
}

export function SafeHaven({ width = 24, height = 24, color = 'currentColor', className }: Props) {
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
      {/* Shield outline */}
      <path d="M12 2 L20 5.5 V12 C20 16.5 16.5 20.5 12 22 C7.5 20.5 4 16.5 4 12 V5.5 Z" />
      {/* Vertical bar of exclamation */}
      <line x1="12" y1="9" x2="12" y2="14" />
      {/* Dot of exclamation */}
      <circle cx="12" cy="16.5" r="0.75" fill={color} stroke="none" />
    </svg>
  );
}
