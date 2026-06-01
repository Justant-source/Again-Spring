'use client';

import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { cn } from '@/lib/utils';

/**
 * Responsive phone-frame container used across every screen. On desktop it
 * renders inside a fixed 360×760 frame (matches the mockup); on mobile it
 * expands to fill the viewport so real usage is not letterboxed.
 */
export function PhoneFrame({
  children,
  tone = 'L',
  className,
}: {
  children: ReactNode;
  tone?: 'L' | 'P' | 'Q';
  className?: string;
}) {
  return (
    <div
      className={cn(
        'min-h-screen w-full flex justify-center',
        tone === 'L' && 'tone-L',
        tone === 'P' && 'tone-P',
        tone === 'Q' && 'tone-Q',
        className,
      )}
    >
      <div className="w-full max-w-[420px] min-h-screen flex flex-col">
        {children}
      </div>
    </div>
  );
}

export function PhoneHeader({
  title,
  tone = 'L',
  back = true,
  right,
  onBack,
}: {
  title?: string;
  tone?: 'L' | 'P';
  back?: boolean;
  right?: ReactNode;
  onBack?: () => void;
}) {
  const router = useRouter();
  const ink = tone === 'P' ? 'var(--P-ink)' : 'var(--L-ink)';
  const sub = tone === 'P' ? 'var(--P-sub)' : 'var(--L-sub)';

  // Fallback to router.back() if onBack not provided
  const handleBack = onBack ?? (() => router.back());

  return (
    <div
      className="flex items-center justify-between px-5 pt-5 pb-3.5"
      style={{ minHeight: 56 }}
    >
      <button
        type="button"
        onClick={handleBack}
        aria-label="뒤로 가기"
        className="w-14 text-[18px] leading-none flex-shrink-0"
        style={{ color: sub, visibility: back ? 'visible' : 'hidden' }}
      >
        ‹
      </button>
      <div
        className="text-[15px] font-medium truncate"
        style={{ color: ink }}
      >
        {title}
      </div>
      <div className="w-14 text-right text-[13px] flex-shrink-0 whitespace-nowrap" style={{ color: sub }}>
        {right}
      </div>
    </div>
  );
}
