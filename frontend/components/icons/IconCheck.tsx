import type { SVGProps } from 'react';

export function IconCheck({ width = 16, height = 16, ...props }: SVGProps<SVGSVGElement> & { width?: number; height?: number }) {
  return (
    <svg
      viewBox="0 0 16 16"
      width={width}
      height={height}
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <polyline points="2.5,8.5 6.5,12.5 13.5,4.5" />
    </svg>
  );
}
