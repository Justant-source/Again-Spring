interface Props {
  width?: number;
  height?: number;
  color?: string;
  className?: string;
}

export function DasibomLogo({ width = 32, height = 32, color = 'currentColor', className }: Props) {
  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 32 32"
      fill="none"
      stroke={color}
      strokeWidth="1.75"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <path d="M16 28 L16 14" />
      <path d="M16 20 C12 17 7 18 6 22 C8 26 14 25 16 20 Z" />
      <path d="M16 15 C20 11 25 12 25 16 C24 20 18 19 16 15 Z" />
    </svg>
  );
}
