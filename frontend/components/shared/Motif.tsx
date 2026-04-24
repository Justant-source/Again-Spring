import type { CommunicationStyle } from '@/lib/types';

type IconProps = { size?: number; color?: string };

export function MotifWave({ size = 32, color = 'currentColor' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 22c3-3 5-3 8 0s5 3 8 0 5-3 8 0 5 3 8 0" />
      <path d="M4 30c3-3 5-3 8 0s5 3 8 0 5-3 8 0 5 3 8 0" opacity="0.5" />
    </svg>
  );
}

export function MotifMountain({ size = 32, color = 'currentColor' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 30l10-14 8 11 4-5 10 8" />
      <path d="M4 30h32" opacity="0.5" />
    </svg>
  );
}

export function MotifFlame({ size = 32, color = 'currentColor' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 6c-2 6-8 9-8 16a8 8 0 0 0 16 0c0-4-2-6-4-8-1 2-2 3-4 3 1-4 1-7 0-11z" />
    </svg>
  );
}

export function MotifLeaf({ size = 32, color = 'currentColor' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M8 32c0-14 10-24 24-24 0 14-10 24-24 24z" />
      <path d="M8 32L28 12" opacity="0.5" />
    </svg>
  );
}

export function MotifMoon({ size = 32, color = 'currentColor' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M28 8a12 12 0 1 0 4 20A10 10 0 0 1 28 8z" />
    </svg>
  );
}

export function MotifStar({ size = 32, color = 'currentColor' }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 6l3.5 9 9.5 1-7 6.5 2 9.5L20 27l-8 5 2-9.5-7-6.5 9.5-1z" />
    </svg>
  );
}

export const STYLE_MOTIF: Record<CommunicationStyle, (p: IconProps) => JSX.Element> = {
  wave: MotifWave,
  mountain: MotifMountain,
  flame: MotifFlame,
  leaf: MotifLeaf,
  moon: MotifMoon,
  star: MotifStar,
};

// Icon line set for NVC etc.
function Icon({ children, size = 16, stroke = 1.4 }: { children: React.ReactNode; size?: number; stroke?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" style={{ display: 'inline-block', verticalAlign: '-0.18em' }}>
      {children}
    </svg>
  );
}

export const IconTemp = (p: { size?: number }) => (
  <Icon size={p.size}>
    <path d="M10 14.5V5a2 2 0 0 1 4 0v9.5" />
    <circle cx="12" cy="17" r="3" />
    <line x1="12" y1="8" x2="12" y2="14" />
  </Icon>
);

export const IconMap = (p: { size?: number }) => (
  <Icon size={p.size}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 3v2 M12 19v2 M3 12h2 M19 12h2" />
    <circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none" />
  </Icon>
);

export const IconEye = (p: { size?: number }) => (
  <Icon size={p.size}>
    <path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12z" />
    <circle cx="12" cy="12" r="2.5" />
  </Icon>
);

export const IconDrop = (p: { size?: number }) => (
  <Icon size={p.size}>
    <path d="M12 3c-3 5-6 8-6 11a6 6 0 0 0 12 0c0-3-3-6-6-11z" />
  </Icon>
);

export const IconNeed = (p: { size?: number }) => (
  <Icon size={p.size}>
    <circle cx="12" cy="12" r="8" />
    <circle cx="12" cy="12" r="3" />
  </Icon>
);

export const IconAsk = (p: { size?: number }) => (
  <Icon size={p.size}>
    <path d="M4 16c2-5 6-7 8-7s6 2 8 7" />
    <circle cx="12" cy="6" r="1.2" fill="currentColor" stroke="none" />
  </Icon>
);
