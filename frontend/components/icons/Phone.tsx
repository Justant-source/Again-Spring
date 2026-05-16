interface Props {
  width?: number;
  height?: number;
  color?: string;
  className?: string;
}

export function Phone({ width = 24, height = 24, color = 'currentColor', className }: Props) {
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
      <path d="M6.5 3 H9 L11 8 L8.5 9.5 C9.6 11.8 12.2 14.4 14.5 15.5 L16 13 L21 15 V17.5 C21 19.4 19.2 21 17 21 C9.3 21 3 14.7 3 7 C3 4.8 4.6 3 6.5 3 Z" />
    </svg>
  );
}
