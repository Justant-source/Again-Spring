interface Props {
  width?: number;
  height?: number;
  color?: string;
  className?: string;
}

export function Conversation({ width = 24, height = 24, color = 'currentColor', className }: Props) {
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
      {/* Left bubble (user A) */}
      <path d="M3 6 C3 4.3 4.3 3 6 3 H14 C15.7 3 17 4.3 17 6 V10 C17 11.7 15.7 13 14 13 H8 L5 16 V13 H6 C4.3 13 3 11.7 3 10 Z" />
      {/* Right bubble (user B) — offset, smaller */}
      <path d="M9 9 C9 7.9 9.9 7 11 7 H18 C19.1 7 20 7.9 20 9 V13 C20 14.1 19.1 15 18 15 H16 V17 L13 15 H11 C9.9 15 9 14.1 9 13 Z"
        strokeOpacity="0.45"
      />
    </svg>
  );
}
